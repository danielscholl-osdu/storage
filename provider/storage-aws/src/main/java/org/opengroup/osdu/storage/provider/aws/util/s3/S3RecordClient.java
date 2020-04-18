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

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.aws.s3.S3Config;


import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class S3RecordClient {

    @Inject
    private JaxRsDpsLog logger;

    // Storing all records in one bucket does not impact performance, no need to spread keys anymore
    @Value("${aws.s3.records.bucket-name}")
    private String recordsBucketName;

    @Value("${aws.s3.region}")
    private String s3Region;

    @Value("${aws.s3.endpoint}")
    private String s3Endpoint;

    private AmazonS3 s3;

    @PostConstruct
    public void init() {
        S3Config config = new S3Config(s3Endpoint, s3Region);
        s3 = config.amazonS3();
    }

    /**
     * Upload the record to S3
     * This function is call via threads outside of the request scope and so it CANNOT log messages
     * @param recordProcessing
     */
    public void saveRecord(RecordProcessing recordProcessing) {
        Gson gson = new GsonBuilder().serializeNulls().create();
        RecordMetadata recordMetadata = recordProcessing.getRecordMetadata();
        RecordData recordData = recordProcessing.getRecordData();
        String content = gson.toJson(recordData);
        String keyName = getKeyNameForLatestVersion(recordMetadata);
        s3.putObject(recordsBucketName, keyName, content);
    }

    public String getRecord(RecordMetadata recordMetadata, Long version) {
        String keyName = getKeyNameForVersion(recordMetadata, version);
        return getRecord(keyName);
    }

    public boolean getRecord(RecordMetadata recordMetadata, AtomicReference<Map<String, String>> map) {
        Map<String, String> mapVal = map.get();
        String keyName = getKeyNameForLatestVersion(recordMetadata);
        String record = getRecord(keyName);
        mapVal.put(recordMetadata.getId(), record);
        map.set(mapVal);
        return true;
    }

    public boolean getRecord(String recordId, String versionPath, AtomicReference<Map<String, String>> map) {
        Map<String, String> mapVal = map.get();
        String record = getRecord(versionPath);
        mapVal.put(recordId, record);
        map.set(mapVal);
        return true;
    }

    public void deleteRecord(RecordMetadata recordMetadata) {
        String keyName = getKeyNameForAllVersions(recordMetadata);
        try {
            s3.deleteObject(new DeleteObjectRequest(recordsBucketName, keyName));
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error deleting record", e.getMessage(), e);
        }
    }

    public void deleteRecordVersion(RecordMetadata recordMetadata, Long version) {
        String keyName = getKeyNameForVersion(recordMetadata, version);
        try {
            s3.deleteObject(new DeleteObjectRequest(recordsBucketName, keyName));
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error deleting record", e.getMessage(), e);
        }
    }

    public boolean checkIfRecordExists(RecordMetadata recordMetadata) {
        String keyName = getKeyNameForAllVersions(recordMetadata);
        boolean exists = false;
        try {
            ListObjectsV2Result result = s3.listObjectsV2(recordsBucketName, keyName);
            exists = result.getKeyCount() > 0;
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error finding record", e.getMessage(), e);
        }
        return exists;
    }

    public String getRecord(String keyName) {
        String record = "";
        try {
            record = s3.getObjectAsString(recordsBucketName, keyName);
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error getting record", e.getMessage(), e);
        }
        return record;
    }

    private String getKeyNameForLatestVersion(RecordMetadata recordMetadata) {
        return recordMetadata.getKind() + "/" + recordMetadata.getId() + "/" + recordMetadata.getLatestVersion();
    }

    private String getKeyNameForVersion(RecordMetadata recordMetadata, Long version) {
        return recordMetadata.getVersionPath(version);
    }

    private String getKeyNameForAllVersions(RecordMetadata recordMetadata) {
        return recordMetadata.getKind() + "/" + recordMetadata.getId();
    }
}
