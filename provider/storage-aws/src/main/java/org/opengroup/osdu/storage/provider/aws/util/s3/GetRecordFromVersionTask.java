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

package org.opengroup.osdu.storage.provider.aws.util.s3;

import com.amazonaws.AmazonServiceException;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

class GetRecordFromVersionTask implements Callable<GetRecordFromVersionTask> {
    private S3RecordClient s3RecordClient;
    private AtomicReference<Map<String, String>> map;
    private String versionPath;
    public String recordId;
    public AmazonServiceException exception;
    public CallableResult result;

    public GetRecordFromVersionTask(S3RecordClient s3RecordClient,
                         AtomicReference<Map<String, String>> map,
                         String recordId,
                         String versionPath){
        this.s3RecordClient = s3RecordClient;
        this.map = map;
        this.recordId = recordId;
        this.versionPath = versionPath;
    }

    @Override
    public GetRecordFromVersionTask call() {
        try {
            s3RecordClient.getRecord(this.recordId, this.versionPath, map);
            result = CallableResult.Pass;
        }
        catch(AmazonServiceException e) {
            exception = e;
            result = CallableResult.Fail;
        }
        return this;
    }
}
