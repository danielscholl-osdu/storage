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

import java.util.concurrent.Callable;

import software.amazon.awssdk.awscore.exception.AwsServiceException;

class GetRecordFromVersionTask implements Callable<GetRecordFromVersionTask> {
    private final S3RecordClient s3RecordClient;
    private final String versionPath;
    private final String recordId;
    private String recordContents;
    private Exception exception;
    private CallableResult result;
    private final String dataPartition;

    private static final String EMPTY_S3_MSG = "S3 returned empty record contents";

    public GetRecordFromVersionTask(S3RecordClient s3RecordClient,
                         String recordId,
                         String versionPath,
                         String dataPartition){
        this.s3RecordClient = s3RecordClient;
        this.recordId = recordId;
        this.versionPath = versionPath;
        this.dataPartition = dataPartition;
    }

    @Override
    public GetRecordFromVersionTask call() {
        result = CallableResult.PASS;
        try {
            this.recordContents = s3RecordClient.getRecord(this.versionPath, this.dataPartition);

            if (this.recordContents == null || this.recordContents.equals("")){
                // s3 wasn't ready to deliver contents
                exception = new Exception(EMPTY_S3_MSG);
                result = CallableResult.FAIL;
            }
        }
        catch(AwsServiceException e) {
            exception = e;
            result = CallableResult.FAIL;
        }
        return this;
    }
    
    /**
     * Get the record ID
     * @return the record ID
     */
    public String getRecordId() {
        return recordId;
    }
    
    /**
     * Get the record contents
     * @return the record contents
     */
    public String getRecordContents() {
        return recordContents;
    }
    
    /**
     * Get the result of the callable operation
     * @return the result
     */
    public CallableResult getResult() {
        return result;
    }
    
    /**
     * Get the exception if one occurred
     * @return the exception or null if no exception occurred
     */
    public Exception getException() {
        return exception;
    }
}
