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

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.s3.IS3ClientFactory;
import org.opengroup.osdu.core.aws.s3.S3ClientWithBucket;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.inject.Inject;

@Component
public class S3RecordClient {

    @Inject
    private JaxRsDpsLog logger;    

    @Inject
    private IS3ClientFactory s3ClientFactory;

    @Value("${aws.s3.recordsBucket.ssm.relativePath}")
    private String s3RecordsBucketParameterRelativePath;

    @Inject
    private WorkerThreadPool workerThreadPool;

    private static final String RECORD_DELETE_ERROR_MSG = "Error deleting record";

    private static final String RECORD_FIND_ERROR_MSG = "Error finding record";

    private static final String RECORD_GET_ERROR_MSG = "Error getting record";

    private S3ClientWithBucket getS3ClientWithBucket(String dataPartition) {
        s3ClientFactory.setConfig(workerThreadPool.getClientConfiguration());
        return s3ClientFactory.getS3ClientForPartition(dataPartition, s3RecordsBucketParameterRelativePath);
    } 

    /**
     * Upload the record to S3
     * This function is call via threads outside of the request scope and so it CANNOT log messages
     * @param recordProcessing
     */
    public void saveRecord(RecordProcessing recordProcessing, String dataPartition) {        

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();


        Gson gson = new GsonBuilder().serializeNulls().create();
        RecordMetadata recordMetadata = recordProcessing.getRecordMetadata();
        RecordData recordData = recordProcessing.getRecordData();
        String content = gson.toJson(recordData);
        String keyName = getKeyNameForLatestVersion(recordMetadata);
        s3.putObject(recordsBucketName, keyName, content);
    }

    public String getRecord(RecordMetadata recordMetadata, Long version, String dataPartition) {
        String keyName = getKeyNameForVersion(recordMetadata, version);
        return getRecord(keyName, dataPartition);
    }

    public boolean getRecord(RecordMetadata recordMetadata, AtomicReference<Map<String, String>> map, String dataPartition) {
        Map<String, String> mapVal = map.get();
        String keyName = getKeyNameForLatestVersion(recordMetadata);
        String recordStr = getRecord(keyName, dataPartition);
        mapVal.put(recordMetadata.getId(), recordStr);
        map.set(mapVal);
        return true;
    }

    public boolean getRecord(String recordId, String versionPath, AtomicReference<Map<String, String>> map, String dataPartition) {
        Map<String, String> mapVal = map.get();
        String recordStr = getRecord(versionPath, dataPartition);
        mapVal.put(recordId, recordStr);
        map.set(mapVal);
        return true;
    }

    public void deleteRecord(String keyName, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        
        try {
            s3.deleteObject(new DeleteObjectRequest(recordsBucketName, keyName));
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public void deleteRecordVersion(RecordMetadata recordMetadata, Long version, String dataPartition) {
        
        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();

        String keyName = getKeyNameForVersion(recordMetadata, version);
        try {
            s3.deleteObject(new DeleteObjectRequest(recordsBucketName, keyName));
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public void deleteRecordVersion(String versionPath, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        try {
            s3.deleteObject(new DeleteObjectRequest(recordsBucketName, versionPath));
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public boolean checkIfRecordExists(RecordMetadata recordMetadata, String dataPartition) {
        
        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        
        String keyName = getKeyNameForAllVersions(recordMetadata);
        boolean exists = false;
        try {
            ListObjectsV2Result result = s3.listObjectsV2(recordsBucketName, keyName);
            exists = result.getKeyCount() > 0;
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_FIND_ERROR_MSG, e.getMessage(), e);
        }
        return exists;
    }

    public String getRecord(String keyName, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        AmazonS3 s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();

        String recordStr = "";
        try {
            recordStr = s3.getObjectAsString(recordsBucketName, keyName);
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_GET_ERROR_MSG, e.getMessage(), e);
        }
        return recordStr;
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
