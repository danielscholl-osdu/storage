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

package org.opengroup.osdu.storage.provider.aws.util.s3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component
public class RecordsUtil {

    @Inject
    private JaxRsDpsLog logger;

    private S3RecordClient s3RecordClient;

    @Inject
    private WorkerThreadPool threadPool;

    @Inject
    private DpsHeaders headers;

    public RecordsUtil(S3RecordClient s3RecordClient){
        this.s3RecordClient = s3RecordClient;       
    }

    public Map<String, String> getRecordsValuesById(Map<String, String> objects) {

        String dataPartition = headers.getPartitionIdWithFallbackToAccountId();


        Map<String, String> map = new HashMap<>();
        List<CompletableFuture<GetRecordFromVersionTask>> futures = new ArrayList<>();

        try {
            for (Map.Entry<String, String> object : objects.entrySet()) {
                GetRecordFromVersionTask task = new GetRecordFromVersionTask(s3RecordClient, object.getKey(), object.getValue(), dataPartition);
                CompletableFuture<GetRecordFromVersionTask> future = CompletableFuture.supplyAsync(task::call, threadPool.getThreadPool());
                futures.add(future);
            }

            CompletableFuture<?>[] cfs = futures.toArray(CompletableFuture[]::new);
            CompletableFuture<List<GetRecordFromVersionTask>> results = CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));

            List<GetRecordFromVersionTask> getRecordFromVersionTasks = results.get();
            for (GetRecordFromVersionTask task : getRecordFromVersionTasks) {
                if (task.getException() != null
                        || task.getResult() == CallableResult.FAIL) {

                            logger.error(String.format("%s failed getting record from S3 with exception: %s"
                            , task.getRecordId()
                            , task.getException().getMessage()
                    ));
                } else {
                    map.put(task.getRecordId(), task.getRecordContents());
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

        return map;
    }

    public Map<String, String> getRecordsValuesById(Collection<RecordMetadata> recordMetadatas) {
        
        String dataPartition = headers.getPartitionIdWithFallbackToAccountId();

        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        map.set(new HashMap<>());
        List<CompletableFuture<GetRecordTask>> futures = new ArrayList<>();

        try {
            for (RecordMetadata recordMetadata: recordMetadatas) {
                GetRecordTask task = new GetRecordTask(s3RecordClient, map, recordMetadata, dataPartition);
                CompletableFuture<GetRecordTask> future = CompletableFuture.supplyAsync(task::call, threadPool.getThreadPool());
                futures.add(future);
            }

            CompletableFuture<?>[] cfs = futures.toArray(CompletableFuture[]::new);
            CompletableFuture<List<GetRecordTask>> results = CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));

            List<GetRecordTask> getRecordTasks = results.get();
            for (GetRecordTask task : getRecordTasks) {
                if (task.getException() != null
                        || task.getResult() == CallableResult.FAIL) {
                    logger.error(String.format("%s failed writing to S3 with exception: %s"
                            , task.getRecordMetadata().getId()
                            , task.getException().getMessage()
                    ));
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

        return map.get();
    }
}
