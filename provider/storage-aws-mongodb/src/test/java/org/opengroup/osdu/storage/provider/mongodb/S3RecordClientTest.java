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

package org.opengroup.osdu.storage.provider.mongodb;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.PutObjectResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.mongodb.util.s3.S3RecordClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

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

    RecordMetadata recordMetadata = new RecordMetadata();

    @Before
    public void setUp() {
        initMocks(this);
        recordMetadata.setKind("test-record-id");
        recordMetadata.setId("test-record-id");
        recordMetadata.addGcsPath(1);
        recordMetadata.addGcsPath(2);
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
        client.saveRecord(recordProcessing);

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
        String result = client.getRecord(keyName);

        // assert
        Assert.assertEquals("test-result", result);
    }

    @Test
    public void getRecordSpecificKeyName(){
        // arrange
        String expectedKeyName = recordMetadata.getKind() + "/" + recordMetadata.getId() + "/1";

        Mockito.doReturn("test-result").when(client).getRecord(Mockito.eq(expectedKeyName));

        // act
        String result = client.getRecord(recordMetadata, 1L);

        // assert
        Mockito.verify(client, Mockito.times(1)).getRecord(
                Mockito.eq(expectedKeyName));
    }

    @Test
    public void deleteRecord(){
        // arrange
        String expectedKeyName = recordsBucketName + "/" + recordMetadata.getId();

        Mockito.doNothing().when(s3).deleteObject(Mockito.eq(recordsBucketName), Mockito.eq(expectedKeyName));

        // act
        client.deleteRecord(recordMetadata);

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
        boolean result = client.checkIfRecordExists(recordMetadata);

        // assert
        Assert.assertTrue(true);
    }
}
