// Copyright © Amazon Web Services
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
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordProcessor;
import org.opengroup.osdu.storage.provider.aws.util.s3.CallableResult;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordsUtil;
import org.opengroup.osdu.storage.provider.aws.util.s3.S3RecordClient;
import org.apache.http.HttpStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Repository
public class CloudStorageImpl implements ICloudStorage {

    @Value("${aws.s3.max-record-threads}")
    private int maxNumOfRecordThreads;

    @Inject
    private S3RecordClient s3RecordClient;

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private RecordsUtil recordsUtil;

    private ExecutorService threadPool;

    @PostConstruct
    public void init(){
       this.threadPool = Executors.newFixedThreadPool(maxNumOfRecordThreads);
    }

    // Used specifically in the unit tests
    public void init(ExecutorService threadPool){
        this.threadPool = threadPool;
    }

    @Override
    public void write(RecordProcessing... recordsProcessing) {
        // TODO: throughout this class userId isn't used, seems to be something to integrate with entitlements service
        // TODO: ensure that the threads come from the shared pool manager from the web server
        // Using threads to write records to S3 to increase efficiency, no impact to cost
        List<CompletableFuture<RecordProcessor>> futures = new ArrayList<>();

        for(RecordProcessing recordProcessing : recordsProcessing){
            if(recordProcessing.getRecordData().getMeta() == null) {
                HashMap<String, Object> meta = new HashMap<String, Object>();
                HashMap<String,Object>[] arrayMeta = new HashMap[0];
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
        Gson gson = new Gson();
        Map<String, String> base64Hashes = new HashMap<String, String>();
        Map<String, String> recordsMap = recordsUtil.getRecordsValuesById(records);
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
        s3RecordClient.deleteRecord(record);
    }

    @Override
    public void deleteVersion(RecordMetadata record, Long version) {
        s3RecordClient.deleteRecordVersion(record, version);
    }

    @Override
    public boolean hasAccess(RecordMetadata... records) {
        boolean hasAccess = true;
        for (RecordMetadata record : records) {
            hasAccess = s3RecordClient.checkIfRecordExists(record);
        }
        return hasAccess;
    }

    @Override
    public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
        // checkDataInconsistency not used in other providers
        return s3RecordClient.getRecord(record, version);
    }

    @Override
    public Map<String, String> read(Map<String, String> objects) {
        // key -> record id
        // value -> record version path
        return recordsUtil.getRecordsValuesById(objects);
    }

    @Override
    public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
        return s3RecordClient.isDuplicateRecord(kv.getKey(), kv.getValue());
    }
}
