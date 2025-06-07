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

import com.amazonaws.AmazonServiceException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

class GetRecordTask implements Callable<GetRecordTask> {
    private final S3RecordClient s3RecordClient;
    private final AtomicReference<Map<String, String>> map;
    private final RecordMetadata recordMetadata;
    private AmazonServiceException exception;
    private CallableResult result;
    private final String dataPartition;

    public GetRecordTask(S3RecordClient s3RecordClient,
                         AtomicReference<Map<String, String>> map,
                         RecordMetadata recordMetadata,
                         String dataPartition){
        this.s3RecordClient = s3RecordClient;
        this.map = map;
        this.recordMetadata = recordMetadata;
        this.dataPartition = dataPartition;
    }

    @Override
    public GetRecordTask call() {
        try{
            s3RecordClient.getRecord(recordMetadata, map, dataPartition);
            result = CallableResult.PASS;
        }
         catch(AmazonServiceException e) {
            result = CallableResult.FAIL;
            exception = e;
        }
        return this;
    }
    
    /**
     * Get the record metadata
     * @return the record metadata
     */
    public RecordMetadata getRecordMetadata() {
        return recordMetadata;
    }
    
    /**
     * Get the exception if one occurred
     * @return the exception or null if no exception occurred
     */
    public AmazonServiceException getException() {
        return exception;
    }
    
    /**
     * Get the result of the callable operation
     * @return the result
     */
    public CallableResult getResult() {
        return result;
    }
}
