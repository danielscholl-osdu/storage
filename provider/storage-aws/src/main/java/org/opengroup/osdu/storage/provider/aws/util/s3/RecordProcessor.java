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

import software.amazon.awssdk.awscore.exception.AwsServiceException;

import org.opengroup.osdu.core.common.model.storage.RecordProcessing;

import java.util.concurrent.Callable;

public class RecordProcessor implements Callable<RecordProcessor> {
    private final RecordProcessing recordProcessing;
    private final S3RecordClient s3Client;
    private CallableResult result;
    private AwsServiceException exception;
    private final String recordId;
    private final String dataPartition;

    public RecordProcessor(RecordProcessing recordProcessing, S3RecordClient s3Client, String dataPartition){
        this.recordProcessing = recordProcessing;
        this.s3Client = s3Client;
        this.dataPartition = dataPartition;
        recordId = recordProcessing.getRecordMetadata().getId();
    }

    @Override
    public RecordProcessor call() {
        try {
            s3Client.saveRecord(recordProcessing, dataPartition);
            result = CallableResult.PASS;
        }
        catch(AwsServiceException e) {
            this.exception = e;
            result = CallableResult.FAIL;
        }
        return this;
    }

    public String getRecordId() {
        return recordId;
    }

    public CallableResult getResult() {
        return result;
    }

    public AwsServiceException getException() {
        return exception;
    }
}
