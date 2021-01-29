// Copyright © 2020 Amazon Web Services
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.mongodb;

import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.mongodb.security.UserAccessService;
import org.opengroup.osdu.storage.provider.mongodb.util.s3.CallableResult;
import org.opengroup.osdu.storage.provider.mongodb.util.s3.RecordProcessor;
import org.opengroup.osdu.storage.provider.mongodb.util.s3.RecordsUtil;
import org.opengroup.osdu.storage.provider.mongodb.util.s3.S3RecordClient;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

@Repository
public class CloudStorageImpl implements ICloudStorage {

    @Value("${aws.dynamodb.table.prefix}")
    String tablePrefix;

    @Value("${aws.region}")
    String dynamoDbRegion;

    @Value("${aws.dynamodb.endpoint}")
    String dynamoDbEndpoint;

    @Value("${aws.s3.max-record-threads}")
    private int maxNumOfRecordThreads;

    @Inject
    private S3RecordClient s3RecordClient;

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private RecordsUtil recordsUtil;

    @Inject
    private UserAccessService userAccessService;

    @Inject
    private IRecordsMetadataRepository recordsMetadataRepository;

    private ExecutorService threadPool;

    private DynamoDBQueryHelper queryHelper;

    @PostConstruct
    public void init(){
       this.threadPool = Executors.newFixedThreadPool(maxNumOfRecordThreads);
        queryHelper = new DynamoDBQueryHelper(dynamoDbEndpoint, dynamoDbRegion, tablePrefix);
    }

    // Used specifically in the unit tests
    public void init(ExecutorService threadPool){
        this.threadPool = threadPool;
    }

    @Override
    public void write(RecordProcessing... recordsProcessing) {
        userAccessService.validateRecordAcl(this.queryHelper, recordsProcessing);

        // TODO: throughout this class userId isn't used, seems to be something to integrate with entitlements service
        // TODO: ensure that the threads come from the shared pool manager from the web server
        // Using threads to write records to S3 to increase efficiency, no impact to cost
        List<CompletableFuture<RecordProcessor>> futures = new ArrayList<>();

        for(RecordProcessing recordProcessing : recordsProcessing){
            if (recordProcessing.getRecordData().getMeta() == null) {
                HashMap<String, Object> meta = new HashMap<String, Object>();
                HashMap<String, Object>[] arrayMeta = new HashMap[0];
                recordProcessing.getRecordData().setMeta(arrayMeta);
            }
            RecordProcessor recordProcessor = new RecordProcessor(recordProcessing, s3RecordClient);
            CompletableFuture<RecordProcessor> future = CompletableFuture.supplyAsync(recordProcessor::call);
            futures.add(future);
        }

        try {
            CompletableFuture[] cfs = futures.toArray(new CompletableFuture[0]);
            CompletableFuture<List<RecordProcessor>> results =  CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList()));

            List<RecordProcessor> recordProcessors = results.get();
            for(RecordProcessor recordProcessor : recordProcessors){
                if(recordProcessor.exception != null
                        || recordProcessor.result == CallableResult.Fail){
                    assert recordProcessor.exception != null;
                    logger.error(String.format("%s failed writing to S3 with exception: %s"
                            , recordProcessor.recordId
                            , recordProcessor.exception.getErrorMessage()
                    ));
                }
            }
        } catch (Exception e) {
            if (e.getCause() instanceof AppException) {
                throw (AppException) e.getCause();
            } else {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error during record ingestion",
                        e.getMessage(), e);
            }
        }
    }

    @Override
    public Map<String, String> getHash(Collection<RecordMetadata> records) {
        Collection<RecordMetadata> accessibleRecords = new ArrayList<>();

        for (RecordMetadata record : records) {
            if (userAccessService.userHasAccessToRecord(record.getAcl())) {
                accessibleRecords.add(record);
            }
        }

        Gson gson = new Gson();
        Map<String, String> base64Hashes = new HashMap<String, String>();
        Map<String, String> recordsMap = recordsUtil.getRecordsValuesById(accessibleRecords);
        for (Map.Entry<String, String> recordObj : recordsMap.entrySet()) {
            String recordId = recordObj.getKey();
            String contents = recordObj.getValue();
            RecordData data = gson.fromJson(contents, RecordData.class);
            String dataContents = gson.toJson(data);
            byte[] bytes = dataContents.getBytes(StandardCharsets.UTF_8);
            Crc32c checksumGenerator = new Crc32c();
            checksumGenerator.update(bytes, 0, bytes.length);
            bytes = checksumGenerator.getValueAsBytes();
            String newHash = new String(encodeBase64(bytes));
            base64Hashes.put(recordId, newHash);
        }
        return base64Hashes;
    }

    @Override
    public void delete(RecordMetadata record) {
        if (!record.hasVersion()) {
            this.logger.warning(String.format("Record %s does not have versions available", record.getId()));
            return;
        }

        if(userAccessService.userHasAccessToRecord(record.getAcl())) {
            s3RecordClient.deleteRecord(record);
        } else {
            logger.error(String.format("User not in ACL for record %s", record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                    "The user is not authorized to perform this action");
        }
    }

    @Override
    public void deleteVersion(RecordMetadata record, Long version) {
        if(userAccessService.userHasAccessToRecord(record.getAcl())) {
            s3RecordClient.deleteRecordVersion(record, version);
        } else {
            logger.error(String.format("User not in ACL for record %s", record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                    "The user is not authorized to perform this action");
        }
    }

    @Override
    public boolean hasAccess(RecordMetadata... records) {
        for (RecordMetadata recordMetadata : records) {
            if (!recordMetadata.hasVersion()) {
                this.logger.warning(String.format("Record %s does not have versions available", recordMetadata.getId()));
                continue;
            }

            if (!userAccessService.userHasAccessToRecord(recordMetadata.getAcl())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
        // checkDataInconsistency not used in other providers
        if(userAccessService.userHasAccessToRecord(record.getAcl())) {
            return s3RecordClient.getRecord(record, version);
        } else {
            logger.error(String.format("User not in ACL for record %s", record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                    "The user is not authorized to perform this action");
        }
    }

    @Override
    public Map<String, String> read(Map<String, String> objects) {
        // key -> record id
        // value -> record version path
        return recordsUtil.getRecordsValuesById(objects);
    }

    @Override
    public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
        RecordMetadata metadata = kv.getKey();
        RecordData recordData = kv.getValue();

        Gson gson = new Gson();
        String dataContents = gson.toJson(recordData.getData());
        String originalDataContents = s3RecordClient.getRecord(metadata, metadata.getLatestVersion());
        RecordData originalRecordData = gson.fromJson(originalDataContents, RecordData.class);
        originalDataContents = gson.toJson(originalRecordData.getData());
        String newHash = Base64.getEncoder().encodeToString(dataContents.getBytes());
        String originalHash = Base64.getEncoder().encodeToString(originalDataContents.getBytes());

        if (newHash.equals(originalHash)) {
            transfer.getSkippedRecords().add(metadata.getId());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap) {

        Map<String, Acl> originalAcls = new HashMap<>();
        Map<String, RecordMetadata> currentRecords = this.recordsMetadataRepository.get(recordsId);

        for (RecordMetadata recordMetadata : recordsMetadata) {
            String id = recordMetadata.getId();
            String idWithVersion = recordsIdMap.get(id);

            if (!id.equalsIgnoreCase(idWithVersion)) {
                long previousVersion = Long.parseLong(idWithVersion.split(":")[3]);
                long currentVersion = currentRecords.get(id).getLatestVersion();
                if (previousVersion != currentVersion) {
                    lockedRecords.add(idWithVersion);
                    continue;
                }
            }
            validMetadata.add(recordMetadata);
            originalAcls.put(recordMetadata.getId(), currentRecords.get(id).getAcl());
        }
        return originalAcls;
    }

    @Override
    public void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, Acl> originalAcls) {
        List<RecordMetadata> originalAclRecords = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            Acl acl = originalAcls.get(recordMetadata.getId());
            recordMetadata.setAcl(acl);
            originalAclRecords.add(recordMetadata);
        }
        try {
            this.recordsMetadataRepository.createOrUpdate(originalAclRecords);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error while reverting metadata: in revertObjectMetadata.","Internal server error.", e);
        }
    }


}
