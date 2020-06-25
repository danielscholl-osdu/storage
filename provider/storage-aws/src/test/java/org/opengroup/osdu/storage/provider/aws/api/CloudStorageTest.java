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

package org.opengroup.osdu.storage.provider.aws.api;

import com.google.gson.Gson;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.aws.CloudStorageImpl;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordsUtil;
import org.opengroup.osdu.storage.provider.aws.util.s3.S3RecordClient;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class CloudStorageTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private CloudStorageImpl repo = new CloudStorageImpl();

    @Mock
    private S3RecordClient s3RecordClient;

    @Mock
    private RecordsUtil recordsUtil;

    @Mock
    private ExecutorService threadPool;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private IRecordsMetadataRepository recordsMetadataRepository;

    @Inject
    private JaxRsDpsLog logger;

    String userId = "test-user-id";
    RecordMetadata record = new RecordMetadata();
    Collection<RecordMetadata> records = new ArrayList<RecordMetadata>();

    @Before
    public void setUp() {
        initMocks(this);
        record.setId("test-record-id");
        record.addGcsPath(1);
        records.add(record);
    }


    @Test
    public void getHash() throws NoSuchFieldException {
        // arrange
        String mockRecord = "{data:{\"id\":\"test\"}}";
        Map<String, String> mapRecords = new HashMap<String, String>();
        mapRecords.put("test-record-id", mockRecord);
        Gson gson = new Gson();
        RecordData data = gson.fromJson(mockRecord, RecordData.class);
        String dataContents = gson.toJson(data);
        byte[] bytes = dataContents.getBytes(StandardCharsets.UTF_8);
        Crc32c checksumGenerator = new Crc32c();
        checksumGenerator.update(bytes, 0, bytes.length);
        bytes = checksumGenerator.getValueAsBytes();
        String expectedHash = new String(encodeBase64(bytes));

        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.anyObject()))
                .thenReturn(true);
        Mockito.when(recordsUtil.getRecordsValuesById(Mockito.eq(records)))
                .thenReturn(mapRecords);

        // act
        Map<String, String> hashMap = repo.getHash(records);

        // assert
        Assert.assertEquals(expectedHash, hashMap.get("test-record-id"));
    }

    @Test
    public void delete(){
        // arrange
        Mockito.doNothing().when(s3RecordClient).deleteRecord(Mockito.eq(record));
        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.anyObject()))
                .thenReturn(true);

        // act
        repo.delete(record);

        // assert
        Mockito.verify(s3RecordClient, Mockito.times(1)).deleteRecord(record);
    }

    @Test
    public void read(){
        // arrange
        Long version = 1L;
        Mockito.when(s3RecordClient.getRecord(Mockito.eq(record), Mockito.eq(version)))
                .thenReturn("test-response");
        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.anyObject()))
                .thenReturn(true);

        // act
        String resp = repo.read(record, version, false);

        // assert
        Assert.assertEquals("test-response", resp);
    }

    @Test
    public void readMult(){
        // arrange
        Map<String, String> map = new HashMap<>();
        map.put("test-record-id", "test-version-path");

        Map<String, String> expectedResp = new HashMap<>();
        expectedResp.put("test-record-id", "{data:test-data}");

        RecordMetadata recordMetadata = new RecordMetadata();

        Mockito.when(recordsMetadataRepository.get("test-record-id")).thenReturn(recordMetadata);
        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.anyObject()))
                .thenReturn(true);

        Mockito.when(recordsUtil.getRecordsValuesById(Mockito.eq(map)))
                .thenReturn(expectedResp);

        // act
        Map<String, String> resp = repo.read(map);

        // assert
        Assert.assertEquals(expectedResp.get("test-record-id"), resp.get("test-record-id"));
    }
}
