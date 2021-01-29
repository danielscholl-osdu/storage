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

package org.opengroup.osdu.storage.provider.mongodb.util.s3;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class RecordsUtil {

    @Inject
    private JaxRsDpsLog logger;

    private S3RecordClient s3RecordClient;
    private ExecutorService threadPool;

    public RecordsUtil(S3RecordClient s3RecordClient, ExecutorService threadPool){
        this.s3RecordClient = s3RecordClient;
        this.threadPool = threadPool;
    }

    public Map<String, String> getRecordsValuesById(Map<String, String> objects) {
        Map<String, String> map = new HashMap<>();
        List<CompletableFuture<GetRecordFromVersionTask>> futures = new ArrayList<>();

        try {
            for (Map.Entry<String, String> object : objects.entrySet()) {
                GetRecordFromVersionTask task = new GetRecordFromVersionTask(s3RecordClient, object.getKey(), object.getValue());
                CompletableFuture<GetRecordFromVersionTask> future = CompletableFuture.supplyAsync(task::call);
                futures.add(future);
            }

            CompletableFuture[] cfs = futures.toArray(new CompletableFuture[0]);
            CompletableFuture<List<GetRecordFromVersionTask>> results = CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));

            List<GetRecordFromVersionTask> getRecordFromVersionTasks = results.get();
            for (GetRecordFromVersionTask task : getRecordFromVersionTasks) {
                if (task.exception != null
                        || task.result == CallableResult.Fail) {
                    assert task.exception != null;
                    logger.error(String.format("%s failed getting record from S3 with exception: %s"
                            , task.recordId
                            , task.exception.getMessage()
                    ));
                } else {
                    map.put(task.recordId, task.recordContents);
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

        return map;
    }

    public Map<String, String> getRecordsValuesById(Collection<RecordMetadata> recordMetadatas) {
        AtomicReference<Map<String, String>> map = new AtomicReference<>();
        map.set(new HashMap<>());
        List<CompletableFuture<GetRecordTask>> futures = new ArrayList<>();

        try {
            for (RecordMetadata recordMetadata: recordMetadatas) {
                GetRecordTask task = new GetRecordTask(s3RecordClient, map, recordMetadata);
                CompletableFuture<GetRecordTask> future = CompletableFuture.supplyAsync(task::call);
                futures.add(future);
            }

            CompletableFuture[] cfs = futures.toArray(new CompletableFuture[0]);
            CompletableFuture<List<GetRecordTask>> results = CompletableFuture.allOf(cfs)
                    .thenApply(ignored -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()));

            List<GetRecordTask> getRecordTasks = results.get();
            for (GetRecordTask task : getRecordTasks) {
                if (task.exception != null
                        || task.result == CallableResult.Fail) {
                    logger.error(String.format("%s failed writing to S3 with exception: %s"
                            , task.recordMetadata.getId()
                            , task.exception.getErrorMessage()
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

        return map.get();
    }
}
