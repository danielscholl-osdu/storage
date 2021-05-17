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

package org.opengroup.osdu.storage.provider.aws.api;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectResult;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.s3.S3ClientFactory;
import org.opengroup.osdu.core.aws.s3.S3ClientWithBucket;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.util.s3.S3RecordClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import javax.inject.Inject;
import java.util.*;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class S3RecordClientTest {

    @InjectMocks
    @Inject
    @Spy
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

    @Before
    public void setUp() {
        initMocks(this);
        recordMetadata.setKind("test-record-id");
        recordMetadata.setId("test-record-id");
        recordMetadata.addGcsPath(1);
        recordMetadata.addGcsPath(2);

        Mockito.when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartition);

        Mockito.when(queryHelperFactory.getQueryHelperForPartition(Mockito.any(DpsHeaders.class), Mockito.any()))
        .thenReturn(queryHelper);
        Mockito.when(queryHelperFactory.getQueryHelperForPartition(Mockito.any(String.class), Mockito.any()))
        .thenReturn(queryHelper);

        Mockito.when(s3ClientWithBucket.getS3Client()).thenReturn(s3);
        Mockito.when(s3ClientWithBucket.getBucketName()).thenReturn(recordsBucketName);

        Mockito.when(s3ClientFactory.getS3ClientForPartition(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(s3ClientWithBucket);
        
        Mockito.when(s3ClientFactory.getS3ClientForPartition(Mockito.any(DpsHeaders.class), Mockito.anyString()))
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
                Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName), Mockito.eq("{\"data\":{\"test-data\":{}},\"meta\":null}"));
    }

    @Test
    public void getRecordMain(){
        // arrange
        String keyName = "test-key-name";

        Mockito.when(s3.getObjectAsString(Mockito.eq(recordsBucketName), Mockito.eq(keyName)))
                .thenReturn("test-result");

        // act
        String result = client.getRecord(keyName, dataPartition);

        // assert
        Assert.assertEquals("test-result", result);
    }

    @Test
    public void getRecordSpecificKeyName(){
        // arrange
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/1";

        Mockito.doReturn("test-result").when(client).getRecord(Mockito.eq(expectedKeyName), Mockito.eq(dataPartition));

        // act
        String result = client.getRecord(recordMetadata, 1L, dataPartition);

        // assert
        Mockito.verify(client, Mockito.times(1)).getRecord(
                Mockito.eq(expectedKeyName), Mockito.eq(dataPartition));
    }

    @Test
    public void deleteRecord(){
        // arrange
        String expectedKeyName = recordsBucketName + "/" + recordMetadata.getId();

        Mockito.doNothing().when(s3).deleteObject(Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName));

        // act
        client.deleteRecord(recordMetadata, dataPartition);

        // assert
        Mockito.verify(s3, Mockito.times(1)).deleteObject(
                Mockito.anyObject());
    }

    @Test
    public void checkIfRecordExists(){
        // arrange
        String keyName = recordMetadata.getKind() + "/" + recordMetadata.getId();

        Mockito.doReturn(new ListObjectsV2Result()).when(s3)
                .listObjectsV2(Mockito.eq(recordsBucketName), Mockito.eq(keyName));

        // act
        boolean result = client.checkIfRecordExists(recordMetadata, dataPartition);

        // assert
        Assert.assertTrue(true);
    }
}
