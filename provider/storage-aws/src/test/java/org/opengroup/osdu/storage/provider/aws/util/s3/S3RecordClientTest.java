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

import org.mockito.ArgumentCaptor;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.core.exception.SdkClientException;


import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.v2.s3.S3ClientFactory;
import org.opengroup.osdu.core.aws.v2.s3.S3ClientWithBucket;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;


class S3RecordClientTest {

    @InjectMocks
    private S3RecordClient client;

    private String recordsBucketName;

    @Mock
    private S3Client s3;

    @Mock
    private S3ClientFactory s3ClientFactory;

    RecordMetadata recordMetadata = new RecordMetadata();
    
    private String dataPartition = "dummyPartitionName";    

    @Mock
    private WorkerThreadPool workerThreadPool;

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
    void setUp() {
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
    void save() throws IOException {
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

        Mockito.when(s3.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
                .thenReturn(null);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        // act
        client.saveRecord(recordProcessing, dataPartition);

        // assert
        verify(s3).putObject(requestCaptor.capture(), bodyCaptor.capture());
        PutObjectRequest requestArgument = requestCaptor.getValue();
        RequestBody bodyArgument = bodyCaptor.getValue();
        String requestBodyString = new String(bodyArgument.contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals( expectedKeyName, requestArgument.key());
        assertEquals("{\"data\":{\"test-data\":{}},\"meta\":null,\"modifyUser\":null,\"modifyTime\":0}", requestBodyString);

        Mockito.verify(s3, Mockito.times(1)).putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class));
    }

    @Test
    void getRecordMain(){
        // arrange

        String expectedRecordContent = "test-result";
        ResponseBytes<GetObjectResponse>  responseByte = (ResponseBytes<GetObjectResponse>) Mockito.mock(ResponseBytes.class);
        Mockito.when(responseByte.asByteArray())
                .thenReturn(expectedRecordContent.getBytes());
        Mockito.when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseByte);

        // act
        String result = client.getRecord(keyName, dataPartition);

        // assert
        assertEquals(expectedRecordContent, result);
        Mockito.verify(s3, Mockito.times(1)).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    void testGetRecordWithVersion() {
        Long version = 1L;
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/" + version;
        String expectedRecordContent = "test-result";

        ResponseBytes<GetObjectResponse>  responseByte = (ResponseBytes<GetObjectResponse>) Mockito.mock(ResponseBytes.class);
        Mockito.when(responseByte.asByteArray())
                .thenReturn(expectedRecordContent.getBytes());
        Mockito.when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseByte);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);

        // act
        String actualRecord = client.getRecord(recordMetadata, version, dataPartition);
        //assert
        verify(s3).getObjectAsBytes(requestCaptor.capture());
        GetObjectRequest requestArgument = requestCaptor.getValue();
        assertEquals( expectedKeyName, requestArgument.key());
        assertEquals(expectedRecordContent, actualRecord);
        
    }
    @Test
    void testGetRecordWithAtomicReferenceMap() {
        Long version = 2L;
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/" + version;
        String expectedRecordContent = "test-record-content";

        ResponseBytes<GetObjectResponse>  responseByte = (ResponseBytes<GetObjectResponse>) Mockito.mock(ResponseBytes.class);
        Mockito.when(responseByte.asByteArray())
                .thenReturn(expectedRecordContent.getBytes());
        Mockito.when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseByte);

        AtomicReference<Map<String, String>> mapReference = new AtomicReference<>(new HashMap<>());

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);

        // act
        boolean result = client.getRecord(recordMetadata, mapReference, dataPartition);

        // assert
        verify(s3).getObjectAsBytes(requestCaptor.capture());
        GetObjectRequest requestArgument = requestCaptor.getValue();
        assertEquals( expectedKeyName, requestArgument.key());
        assertTrue(result);
        Map<String, String> resultMap = mapReference.get();
        assertTrue(resultMap.containsKey(recordMetadata.getId()));
    }

    @Test
    void testGetRecordWithVersionPath(){
        String expectedRecordContent = "test-record-content";
        ResponseBytes<GetObjectResponse>  responseByte = (ResponseBytes<GetObjectResponse>) Mockito.mock(ResponseBytes.class);
        Mockito.when(responseByte.asByteArray())
                .thenReturn(expectedRecordContent.getBytes());
        Mockito.when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseByte);

        AtomicReference<Map<String, String>> mapReference = new AtomicReference<>(new HashMap<>());

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);

        //act
        boolean result = client.getRecord(recordMetadata.getId(), keyName, mapReference, dataPartition);
        //assert
        verify(s3).getObjectAsBytes(requestCaptor.capture());
        GetObjectRequest requestArgument = requestCaptor.getValue();
        assertEquals( keyName, requestArgument.key());
        assertTrue(result);
        Map<String, String> resultMap = mapReference.get();
        assertTrue(resultMap.containsKey(recordMetadata.getId()));
    }

    @Test
    void getRecord_throwsException() {
        // arrange

        Mockito.when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("test-exception").build());

        assertThrows(AppException.class, () -> client.getRecord(keyName, dataPartition));
    }

    @Test
    void deleteRecord(){

        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        // act
        client.deleteRecord(keyName, dataPartition);

        // assert
        Mockito.verify(s3, Mockito.times(1)).deleteObject(
                any(DeleteObjectRequest.class));
    }

    @Test
    void testDeleteRecord_throwsException() {

        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();
        Mockito.when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("test-exception").build());

        // assert
        assertThrows(AppException.class, () -> client.deleteRecord(keyName, dataPartition));
    }

    @Test
    void testDeleteVersion(){
        Mockito.when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(null);
        client.deleteRecordVersion(recordMetadata, 1L, dataPartition);
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void testCheckIfRecordExists() {
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        ListObjectsV2Response response = Mockito.mock(ListObjectsV2Response.class);
        when(response.keyCount()).thenReturn(1);

        Mockito.doReturn(response).when(s3)
                .listObjectsV2(any(ListObjectsV2Request.class));

        ArgumentCaptor<ListObjectsV2Request> requestCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);

        //act
        boolean result = client.checkIfRecordExists(recordMetadata, dataPartition);

        verify(s3).listObjectsV2(requestCaptor.capture());
        ListObjectsV2Request requestArgument = requestCaptor.getValue();
        assertEquals( keyName, requestArgument.prefix());
        assertTrue(result);
    }

    @Test
    void testCheckIfRecordExists_throwsException() {
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        Mockito.doThrow(SdkClientException.builder().message("test-exception").build()).when(s3)
                .listObjectsV2(any(ListObjectsV2Request.class));

        // assert
        assertThrows(AppException.class, () -> client.checkIfRecordExists(recordMetadata, dataPartition));
    }

    @Test
    void testDeleteVersion_throwsException() {
        // arrange
        Mockito.doThrow(SdkClientException.builder().message("test-exception").build()).when(s3)
                .deleteObject(any(DeleteObjectRequest.class));

        // assert
        assertThrows(AppException.class, () -> client.deleteRecordVersion(recordMetadata, 1L, dataPartition));
    }

}
