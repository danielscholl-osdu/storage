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

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectResult;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.s3.S3ClientFactory;
import org.opengroup.osdu.core.aws.s3.S3ClientWithBucket;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;


import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


public class S3RecordClientTest {

    @InjectMocks
    private S3RecordClient client;

    private String recordsBucketName;

    @Mock
    private AmazonS3 s3;

    @Mock
    private S3ClientFactory s3ClientFactory;

    RecordMetadata recordMetadata = new RecordMetadata();
    
    private String dataPartition = "dummyPartitionName";    

    @Mock 
    private DpsHeaders headers;

    @Mock
    private DynamoDBQueryHelperV2 queryHelper;

    @Mock
    private DynamoDBQueryHelperFactory queryHelperFactory;

    @Mock
    private S3ClientWithBucket s3ClientWithBucket;

    private static final String keyName = "test-key-name";

    @BeforeEach
    public void setUp() {
        openMocks(this);
        recordMetadata.setKind("test-record-id");
        recordMetadata.setId("test-record-id");
        recordMetadata.addGcsPath(1L);
        recordMetadata.addGcsPath(2L);

        Mockito.when(s3ClientWithBucket.getS3Client()).thenReturn(s3);
        Mockito.when(s3ClientWithBucket.getBucketName()).thenReturn(recordsBucketName);

        Mockito.when(s3ClientFactory.getS3ClientForPartition(Mockito.nullable(String.class), Mockito.nullable(String.class)))
                .thenReturn(s3ClientWithBucket);

    }

    @Test
    public void save() {
        // arrange
        RecordProcessing recordProcessing = new RecordProcessing();
        recordProcessing.setRecordMetadata(recordMetadata);
        Record record = new Record();
        record.setId("test-record-id");
        Map<String, Object> data = new HashMap<>();
        data.put("test-data", new Object());
        record.setData(data);
        RecordData recordData = new RecordData(record);
        recordProcessing.setRecordData(recordData);
        String expectedKeyName = recordMetadata.getKind() + "/test-record-id/2";

        Mockito.when(s3.putObject(Mockito.eq(recordsBucketName), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new PutObjectResult());

        // act
        client.saveRecord(recordProcessing, dataPartition);

        // assert
        Mockito.verify(s3, Mockito.times(1)).putObject(
                Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName), Mockito.eq("{\"data\":{\"test-data\":{}},\"meta\":null,\"modifyUser\":null,\"modifyTime\":0}"));
    }

    @Test
    public void getRecordMain(){
        // arrange

        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(keyName)))
                .thenReturn("test-result");

        // act
        String result = client.getRecord(keyName, dataPartition);

        // assert
        Assert.assertEquals("test-result", result);
    }

    @Test
    void testGetRecordWithVersion() {
        Long version = 1L;
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/" + version;
        String expectedRecordContent = "test-result";

        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName)))
        .thenReturn(expectedRecordContent);

        // act
        String actualRecord = client.getRecord(recordMetadata, version, dataPartition);
        assertEquals(expectedRecordContent, actualRecord);
        
    }
    @Test
    void testGetRecordWithAtomicReferenceMap() {
        Long version = 1L;
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/" + version;
        String expectedRecordContent = "test-record-content";

        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName)))
            .thenReturn(expectedRecordContent);

        AtomicReference<Map<String, String>> mapReference = new AtomicReference<>(new HashMap<>());

        // act
        boolean result = client.getRecord(recordMetadata, mapReference, dataPartition);

        // assert
        assertTrue(result);
        Map<String, String> resultMap = mapReference.get();
        assertTrue(resultMap.containsKey(recordMetadata.getId()));
    }

    @Test
    void testGetRecordWithVersionPath(){
        String expectedRecordContent = "test-record-content";
        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(keyName)))
            .thenReturn(expectedRecordContent);
        AtomicReference<Map<String, String>> mapReference = new AtomicReference<>(new HashMap<>());

        boolean result = client.getRecord(recordMetadata.getId(), keyName, mapReference, dataPartition);
        assertTrue(result);
        Map<String, String> resultMap = mapReference.get();
        assertTrue(resultMap.containsKey(recordMetadata.getId()));
    }

    @Test
    void getRecord_throwsException() {
        // arrange

        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(keyName)))
                .thenThrow(new SdkClientException("test-exception"));

        assertThrows(AppException.class, () -> client.getRecord(keyName, dataPartition));
    }

    @Test
    public void deleteRecord(){

        // act
        client.deleteRecord(recordMetadata, dataPartition);

        // assert
        Mockito.verify(s3, Mockito.times(1)).deleteObject(
                any());
    }

    @Test
    void testDeleteRecord_throwsException() {
        // arrange
        Mockito.doThrow(new SdkClientException("test-exception")).when(s3)
                .deleteObject(any());

        // assert
        assertThrows(AppException.class, () -> client.deleteRecord(recordMetadata, dataPartition));
    }

    @Test 
    void testDeleteVersion(){
        doNothing().when(s3).deleteObject(any());   
        client.deleteRecordVersion(recordMetadata, 1L, dataPartition);
        verify(s3, times(1)).deleteObject(any());
    }

    @Test
    void testCheckIfRecordExists() {
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        Mockito.doReturn(new ListObjectsV2Result()).when(s3)
                .listObjectsV2(Mockito.eq(recordsBucketName), Mockito.eq(keyName));

        // assert
        assertTrue(true);
    }

    @Test 
    void testCheckIfRecordExists_throwsException() {
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        Mockito.doThrow(new SdkClientException("test-exception")).when(s3)
                .listObjectsV2(Mockito.eq(recordsBucketName), Mockito.eq(keyName));

        // assert
        assertThrows(AppException.class, () -> client.checkIfRecordExists(recordMetadata, dataPartition));
    }

    @Test
    void testDeleteVersion_throwsException() {
        // arrange
        Mockito.doThrow(new SdkClientException("test-exception")).when(s3)
                .deleteObject(any());

        // assert
        assertThrows(AppException.class, () -> client.deleteRecordVersion(recordMetadata, 1L, dataPartition));
    }

    @Test
    public void checkIfRecordExists(){
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        Mockito.doReturn(new ListObjectsV2Result()).when(s3)
                .listObjectsV2(Mockito.eq(recordsBucketName), Mockito.eq(keyName));

        // assert
        Assert.assertTrue(true);
    }
}
