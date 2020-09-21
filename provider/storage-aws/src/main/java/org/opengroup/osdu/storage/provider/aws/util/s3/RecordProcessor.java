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
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;

import java.util.concurrent.Callable;

public class RecordProcessor implements Callable<RecordProcessor> {
    private RecordProcessing recordProcessing;
    private S3RecordClient s3Client;
    public CallableResult result;
    public AmazonServiceException exception;
    public String recordId;

    public RecordProcessor(RecordProcessing recordProcessing, S3RecordClient s3Client){
        this.recordProcessing = recordProcessing;
        this.s3Client = s3Client;
    }

    @Override
    public RecordProcessor call() {
        try {
            recordId = recordProcessing.getRecordMetadata().getId();
            s3Client.saveRecord(recordProcessing);
            result = CallableResult.Pass;
        }
        catch(AmazonServiceException exception) {
            this.exception = exception;
            result = CallableResult.Fail;
        }
        return this;
    }
}
