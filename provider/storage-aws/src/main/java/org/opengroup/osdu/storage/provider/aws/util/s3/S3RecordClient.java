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
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.v2.s3.IS3ClientFactory;
import org.opengroup.osdu.core.aws.v2.s3.S3ClientWithBucket;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Qualifier;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.inject.Inject;

@Component
@Qualifier("S3ClientFactorySDKV2")
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
        s3ClientFactory.setConfig(workerThreadPool.getClientConfiguration(), workerThreadPool.getThreadNumber());
        return s3ClientFactory.getS3ClientForPartition(dataPartition, s3RecordsBucketParameterRelativePath);
    } 

    /**
     * Upload the record to S3
     * This function is call via threads outside of the request scope and so it CANNOT log messages
     * @param recordProcessing
     */
    public void saveRecord(RecordProcessing recordProcessing, String dataPartition) {        

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();


        Gson gson = new GsonBuilder().serializeNulls().create();
        RecordMetadata recordMetadata = recordProcessing.getRecordMetadata();
        RecordData recordData = recordProcessing.getRecordData();
        String content = gson.toJson(recordData);
        String keyName = getKeyNameForLatestVersion(recordMetadata);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(recordsBucketName)
                .key(keyName)
                .build();

        s3.putObject(putObjectRequest, RequestBody.fromString(content));

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
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(recordsBucketName).key(keyName).build());
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public void deleteRecordVersion(RecordMetadata recordMetadata, Long version, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();

        String keyName = getKeyNameForVersion(recordMetadata, version);
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(recordsBucketName).key(keyName).build());
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public void deleteRecordVersion(String versionPath, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(recordsBucketName).key(versionPath).build());
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_DELETE_ERROR_MSG, e.getMessage(), e);
        }
    }

    public boolean checkIfRecordExists(RecordMetadata recordMetadata, String dataPartition) {
        
        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();
        
        String keyName = getKeyNameForAllVersions(recordMetadata);
        boolean exists = false;
        try {
            ListObjectsV2Response result = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(recordsBucketName).prefix(keyName).build());
            exists = result.keyCount() > 0;
        } catch (SdkClientException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_FIND_ERROR_MSG, e.getMessage(), e);
        }
        return exists;
    }

    public String getRecord(String keyName, String dataPartition) {

        S3ClientWithBucket s3ClientWithBucket = getS3ClientWithBucket(dataPartition);
        S3Client s3 = s3ClientWithBucket.getS3Client();
        String recordsBucketName = s3ClientWithBucket.getBucketName();

        String recordStr = "";
        try {
            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(recordsBucketName).key(keyName).build());
            byte[] data = response.asByteArray();
            recordStr = new String(data, StandardCharsets.UTF_8);
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
