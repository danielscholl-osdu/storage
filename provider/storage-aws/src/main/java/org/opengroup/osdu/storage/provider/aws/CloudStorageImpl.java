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

package org.opengroup.osdu.storage.provider.aws;

import com.google.gson.Gson;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordProcessor;
import org.opengroup.osdu.storage.provider.aws.util.s3.CallableResult;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordsUtil;
import org.opengroup.osdu.storage.provider.aws.util.s3.S3RecordClient;
import org.apache.http.HttpStatus;

import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.stereotype.Repository;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Repository
public class CloudStorageImpl implements ICloudStorage {

    @Inject
    private S3RecordClient s3RecordClient;

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private RecordsUtil recordsUtil;

    @Inject
    private UserAccessService userAccessService;

    @Inject
    private IRecordsMetadataRepository<String> recordsMetadataRepository;

    @Inject
    private WorkerThreadPool threadPool;

    @Inject
    private DpsHeaders headers;


    @Override
    public void write(RecordProcessing... recordsProcessing) {
        // We still need to validate that the record's ACL has groups that exist. The current way is to validate
        // that the Service Principal token has access to those groups. We do NOT check the user's groups as the
        // intended behavior is for users to have create-only permission on every group, but only selected users
        // can read, update, or delete those records. See the following comment for more detail:
        // https://community.opengroup.org/osdu/platform/security-and-compliance/policy/-/merge_requests/493#note_317803
        userAccessService.validateRecordAcl(recordsProcessing);

        // throughout this class userId isn't used, seems to be something to integrate with entitlements service
        // ensure that the threads come from the shared pool manager from the web server
        // Using threads to write records to S3 to increase efficiency, no impact to cost
        List<CompletableFuture<RecordProcessor>> futures = new ArrayList<>();

        String dataPartition = headers.getPartitionIdWithFallbackToAccountId();

        for(RecordProcessing recordProcessing : recordsProcessing){
            if (recordProcessing.getRecordData().getMeta() == null) {
                HashMap<String, Object>[] arrayMeta = new HashMap[0];
                recordProcessing.getRecordData().setMeta(arrayMeta);
            }
            RecordProcessor recordProcessor = new RecordProcessor(recordProcessing, s3RecordClient, dataPartition);
            CompletableFuture<RecordProcessor> future = CompletableFuture.supplyAsync(recordProcessor::call, threadPool.getThreadPool());
            futures.add(future);
        }

        try {
            CompletableFuture<?>[] cfs = futures.toArray(CompletableFuture[]::new);
            CompletableFuture<List<RecordProcessor>> results =  CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                    .map(CompletableFuture::join)
                    .toList());

            List<RecordProcessor> recordProcessors = results.get();
            for(RecordProcessor recordProcessor : recordProcessors){
                if(recordProcessor.getException() != null
                        || recordProcessor.getResult() == CallableResult.FAIL){
                    assert recordProcessor.getException() != null;
                    logger.error(String.format("%s failed writing to S3 with exception: %s"
                            , recordProcessor.getRecordId()
                            , recordProcessor.getException().getMessage()
                    ));
                    throw recordProcessor.getException();
                }
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            if (e.getCause() instanceof AppException appException) {
                throw appException;
            } else {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error during record ingestion",
                        e.getMessage(), e);
            }
        }
        
    }

    @Override
    public Map<String, String> getHash(Collection<RecordMetadata> records) {
        Collection<RecordMetadata> accessibleRecords = new ArrayList<>();

        for (RecordMetadata recordMetadata : records) {
            accessibleRecords.add(recordMetadata);
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
    public void delete(RecordMetadata recordMetadata) {
        if (!recordMetadata.hasVersion()) {
            this.logger.warning(String.format("Record %s does not have versions available", recordMetadata.getId()));
            return;
        }

        for(String path : recordMetadata.getGcsVersionPaths()){
            s3RecordClient.deleteRecord(path, headers.getPartitionIdWithFallbackToAccountId());
        }
    }

    @Override
    public void deleteVersion(RecordMetadata recordMetadata, Long version) {
        s3RecordClient.deleteRecordVersion(recordMetadata, version, headers.getPartitionIdWithFallbackToAccountId());
    }

    @Override
    public void deleteVersions(List<String> versionPaths) {
        versionPaths.stream().forEach(versionPath ->
                s3RecordClient.deleteRecordVersion(versionPath, headers.getPartitionIdWithFallbackToAccountId()));
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
    public String read(RecordMetadata recordMetadata, Long version, boolean checkDataInconsistency) {
        return s3RecordClient.getRecord(recordMetadata, version, headers.getPartitionIdWithFallbackToAccountId());
    }

    @Override
    public Map<String, String> read(Map<String, String> objects, Optional<CollaborationContext> collaborationContext) {
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
        String originalDataContents = s3RecordClient.getRecord(metadata, metadata.getLatestVersion(), headers.getPartitionIdWithFallbackToAccountId());
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
    public Map<String, Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap, Optional<CollaborationContext> collaborationContext) {

        Map<String, Acl> originalAcls = new HashMap<>();
        Map<String, RecordMetadata> currentRecords = this.recordsMetadataRepository.get(recordsId, collaborationContext);

        for (RecordMetadata recordMetadata : recordsMetadata) {
            String id = recordMetadata.getId();
            String idWithVersion = recordsIdMap.get(id);

            if (!id.equalsIgnoreCase(idWithVersion)) {
                long previousVersion = Long.parseLong(idWithVersion.split(":")[3]);
                long currentVersion = currentRecords.get(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext)).getLatestVersion();
                if (previousVersion != currentVersion) {
                    lockedRecords.add(idWithVersion);
                    continue;
                }
            }
            validMetadata.add(recordMetadata);
            originalAcls.put(recordMetadata.getId(), currentRecords.get(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext)).getAcl());
        }
        return originalAcls;
    }

    @Override
    public void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, Acl> originalAcls, Optional<CollaborationContext> collaborationContext) {
        List<RecordMetadata> originalAclRecords = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            Acl acl = originalAcls.get(recordMetadata.getId());
            recordMetadata.setAcl(acl);
            originalAclRecords.add(recordMetadata);
        }
        try {
            this.recordsMetadataRepository.createOrUpdate(originalAclRecords, collaborationContext);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error while reverting metadata: in revertObjectMetadata.","Internal server error.", e);
        }
    }

}
