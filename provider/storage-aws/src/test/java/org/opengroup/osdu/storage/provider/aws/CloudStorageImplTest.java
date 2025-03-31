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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.AmazonServiceException;
import com.google.gson.Gson;
import org.opengroup.osdu.core.aws.s3.S3ClientFactory;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.s3.RecordsUtil;
import org.opengroup.osdu.storage.provider.aws.util.s3.S3RecordClient;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.*;

class CloudStorageImplTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private CloudStorageImpl repo;

    @Mock
    private S3RecordClient s3RecordClient;

    @Mock
    private S3ClientFactory s3ClientFactory;

    @Mock
    private RecordsUtil recordsUtil;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private DpsHeaders headers;

    @Mock
    private RecordMetadata record;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private RecordProcessing recordProcessing;

    @Mock
    private RecordData recordData;

    @Mock
    private Acl acl;

    @Mock 
    private RecordsMetadataRepositoryImpl recordsMetadataRepository;

    @Mock
    private TransferInfo transfer;

    private final WorkerThreadPool threadPool = new WorkerThreadPool(10);
    private String dataPartition = "dummyPartitionName";

    private String mockRecord = "{\"data\":{\"id\":\"test\"}, \"meta\":null, \"modifyUser\":null, \"modifyTime\":0}";

    private Gson gson = new Gson();

    String userId = "test-user-id";

    String path = "path";
    

    Collection<RecordMetadata> records = new ArrayList<RecordMetadata>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        doNothing().when(record).setId(userId);
        doNothing().when(record).addGcsPath(1);
        records.add(record);

        ReflectionTestUtils.setField(repo, "threadPool", threadPool);
        when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartition);
    }

    @Test
    void write_shouldNotThrowException_whenRecordProcessingSucceeds() {
        Map<String, Object>[] data = new HashMap[1];
        when(recordProcessing.getRecordData()).thenReturn(recordData);
        when(recordData.getMeta()).thenReturn(data);
        when(recordProcessing.getRecordMetadata()).thenReturn(record);
        when(record.getId()).thenReturn("dummyRecordId");
        doNothing().when(userAccessService).validateRecordAcl(any());

        repo.write(recordProcessing);

        verify(record, times(1)).getId();
        verify(userAccessService, times(1)).validateRecordAcl(recordProcessing);
    }

    @Test
    void write_shouldThrowException_whenRecordProcessingHasException() {
        when(recordProcessing.getRecordData()).thenReturn(recordData);
        doNothing().when(userAccessService).validateRecordAcl(any());

        when(recordProcessing.getRecordMetadata()).thenReturn(record);
        doThrow(AmazonServiceException.class).when(s3RecordClient).saveRecord(recordProcessing, dataPartition);

        assertThrows(AppException.class, () -> repo.write(recordProcessing));
    }

    @Test
    void write_shouldThrowAppException_whenRecordProcessingThrowsException() {
        when(recordProcessing.getRecordData()).thenReturn(recordData);
        doNothing().when(userAccessService).validateRecordAcl(any());
        
        when(recordProcessing.getRecordMetadata()).thenReturn(record);
        doThrow(RuntimeException.class).when(s3RecordClient).saveRecord(recordProcessing, dataPartition);

        assertThrows(AppException.class, () -> repo.write(recordProcessing));
    }

    @Test
    void getHash() throws NoSuchFieldException {
        // arrange
        Map<String, String> mapRecords = new HashMap<String, String>();
        mapRecords.put("test-record-id", mockRecord);
        RecordData data = gson.fromJson(mockRecord, RecordData.class);
        String dataContents = gson.toJson(data);
        byte[] bytes = dataContents.getBytes(StandardCharsets.UTF_8);
        Crc32c checksumGenerator = new Crc32c();
        checksumGenerator.update(bytes, 0, bytes.length);
        bytes = checksumGenerator.getValueAsBytes();
        String expectedHash = new String(encodeBase64(bytes));

        when(recordsUtil.getRecordsValuesById(records))
                .thenReturn(mapRecords);

        // act
        Map<String, String> hashMap = repo.getHash(records);

        // assert
        assertEquals(expectedHash, hashMap.get("test-record-id"));
    }

    @Test
    void delete(){
        // arrange
        Mockito.doNothing().when(s3RecordClient).deleteRecord(path, dataPartition);
        when(record.hasVersion()).thenReturn(true);
        
        List<String> list = new ArrayList<String>();
        list.add(path);
        when(record.getGcsVersionPaths()).thenReturn(list);

        // act
        repo.delete(record);

        // assert
        verify(s3RecordClient, Mockito.times(1)).deleteRecord(path, dataPartition);
    }

    @Test
    void deleteTestNoVersion() {
        when(record.hasVersion()).thenReturn(false);
        List<String> list = new ArrayList<String>();
        list.add(path);
        when(record.getGcsVersionPaths()).thenReturn(list);
        repo.delete(record);
        verify(s3RecordClient, times(0)).deleteRecord(anyString(), any());
    }

    @Test
    void deleteVersion() {
        repo.deleteVersion(record, 1L);
        verify(s3RecordClient, times(1)).deleteRecordVersion(record, 1L, dataPartition);;
    }

    @Test
    void shouldDeleteVersionsSuccessfully() {

        List<String> versionPaths = Arrays.asList("versionPath1", "versionPath2");
        when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartition);

        repo.deleteVersions(versionPaths);

        versionPaths.forEach(versionPath ->
                verify(s3RecordClient, times(1)).deleteRecordVersion(versionPath, dataPartition));

    }


    @Test
    void isDuplicateRecord_shouldReturnTrue_whenRecordIsDuplicate() {
        Map<String, String> hashMap = new HashMap<>();
        Map.Entry<RecordMetadata, RecordData> kv = new AbstractMap.SimpleEntry<>(record, recordData);
        List<String> skippedRecords = new ArrayList<>();
        when(transfer.getSkippedRecords()).thenReturn(skippedRecords);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("key", "value");

        when(record.getLatestVersion()).thenReturn(1L);
        when(recordData.getData()).thenReturn(dataMap);
        when(s3RecordClient.getRecord(record, 1L, dataPartition)).thenReturn("{\"data\": {\"key\": \"value\"}}");

        boolean result = repo.isDuplicateRecord(transfer, hashMap, kv);
        assertTrue(result);
        assertEquals(1, transfer.getSkippedRecords().size());

    }

    @Test
    void isDuplicateRecord_shouldReturnFalse_whenRecordIsNotDuplicate() {
        // Arrange
        Map<String, String> hashMap = new HashMap<>();
        Map.Entry<RecordMetadata, RecordData> kv = new AbstractMap.SimpleEntry<>(record, recordData);
    
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("key", "newValue");
    
        when(record.getLatestVersion()).thenReturn(1L);
        when(recordData.getData()).thenReturn(dataMap);
        when(s3RecordClient.getRecord(record, 1L, dataPartition)).thenReturn("{\"data\": {\"key\": \"value\"}}");
    
        // Act
        boolean result = repo.isDuplicateRecord(transfer, hashMap, kv);
    
        // Assert
        assertFalse(result);
        assertEquals(0, transfer.getSkippedRecords().size());
    }

    @Test
    void read(){
        // arrange
        Long version = 1L;
        when(s3RecordClient.getRecord(eq(record), eq(version), eq(dataPartition)))
                .thenReturn("test-response");

        // act
        String resp = repo.read(record, version, false);

        // assert
        assertEquals("test-response", resp);
    }

    @Test
    void readMult(){
        // arrange
        Map<String, String> map = new HashMap<>();
        map.put("test-record-id", "test-version-path");

        Map<String, String> expectedResp = new HashMap<>();
        expectedResp.put("test-record-id", "{data:test-data}");

        when(recordsUtil.getRecordsValuesById(Mockito.eq(map)))
                .thenReturn(expectedResp);

        // act
        Map<String, String> resp = repo.read(map, Optional.empty());

        // assert
        assertEquals(expectedResp.get("test-record-id"), resp.get("test-record-id"));
    }

    @Test
    void revertObjectMetadata_shouldUpdateRecordMetadataWithOriginalAcls() {
        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(record);
        Map<String, Acl> originalAcls = new HashMap<>();
        originalAcls.put("record1", acl);

        when(record.getId()).thenReturn("record1");

        repo.revertObjectMetadata(recordsMetadata, originalAcls, Optional.empty());

        verify(record, times(1)).setAcl(acl);
        verify(recordsMetadataRepository, times(1)).createOrUpdate(anyList(), eq(Optional.empty()));
    }

    @Test
    void revertObjectMetadata_shouldThrowException_whenCreateOrUpdateFails(){
        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(record);
        Map<String, Acl> originalAcls = new HashMap<>();
        originalAcls.put("record1", acl);

        when(record.getId()).thenReturn("record1");

        when(recordsMetadataRepository.createOrUpdate(anyList(), eq(Optional.empty()))).thenThrow(new RuntimeException("test exception"));

        assertThrows(AppException.class, () -> repo.revertObjectMetadata(recordsMetadata, originalAcls, Optional.empty()));
        
    }

    @Test
    void updateObjectMetadata_shouldAddToValidMetadataAndOriginalAcls_whenIdEqualsIdWithVersion() {
        when(record.getId()).thenReturn("record1:version:1");
        when(record.getAcl()).thenReturn(acl);
        List<RecordMetadata> recordsMetadata = Collections.singletonList(record);
        List<String> recordsId = Collections.singletonList("record1:version:1");
        Map<String, String> recordsIdMap = new HashMap<>();
        recordsIdMap.put("record1:version:1", "record1:version:1");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("record1:version:1", record);

        when(recordsMetadataRepository.get(recordsId, Optional.empty())).thenReturn(currentRecords);

        // Execution
        Map<String, Acl> originalAcls = repo.updateObjectMetadata(recordsMetadata, recordsId, new ArrayList<>(), new ArrayList<>(), recordsIdMap, Optional.empty());

        // Verification
        assertEquals(1, originalAcls.size());
    }

    @Test
    void updateObjectMetadata_shouldAddToLockedRecords_whenIdNotEqualsIdWithVersionAndVersionMismatch() {
        when(record.getId()).thenReturn("record1:type:version:1");
        when(record.getAcl()).thenReturn(acl);
        when(record.getLatestVersion()).thenReturn(1L);
        List<RecordMetadata> recordsMetadata = Collections.singletonList(record);
        List<String> recordsId = Collections.singletonList("record1:type:version:1");
        List<String> lockedRecords = new ArrayList<>();
        Map<String, String> recordsIdMap = new HashMap<>();
        recordsIdMap.put("record1:type:version:1", "record1:type:version:0");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("record1:type:version:1", record);

        when(recordsMetadataRepository.get(recordsId, Optional.empty())).thenReturn(currentRecords);

        repo.updateObjectMetadata(recordsMetadata, recordsId, new ArrayList<>(), lockedRecords, recordsIdMap, Optional.empty());

        // Verification
        assertEquals(1, lockedRecords.size());
        assertEquals("record1:type:version:0", lockedRecords.get(0));
    }

    @Test
void hasAccess_shouldReturnTrue_whenUserHasAccessToAllRecords() {
    // Arrange
    RecordMetadata record1 = mock(RecordMetadata.class);
    RecordMetadata record2 = mock(RecordMetadata.class);
    Acl acl1 = mock(Acl.class);
    Acl acl2 = mock(Acl.class);

    when(record1.hasVersion()).thenReturn(true);
    when(record2.hasVersion()).thenReturn(true);
    when(record1.getAcl()).thenReturn(acl1);
    when(record2.getAcl()).thenReturn(acl2);
    when(userAccessService.userHasAccessToRecord(acl1)).thenReturn(true);
    when(userAccessService.userHasAccessToRecord(acl2)).thenReturn(true);

    // Act
    boolean result = repo.hasAccess(record1, record2);

    // Assert
    assertTrue(result);
}

@Test
void hasAccess_shouldReturnFalse_whenUserDoesNotHaveAccessToAtLeastOneRecord() {
    // Arrange
    RecordMetadata record1 = mock(RecordMetadata.class);
    RecordMetadata record2 = mock(RecordMetadata.class);
    Acl acl1 = mock(Acl.class);
    Acl acl2 = mock(Acl.class);

    when(record1.hasVersion()).thenReturn(true);
    when(record2.hasVersion()).thenReturn(true);
    when(record1.getAcl()).thenReturn(acl1);
    when(record2.getAcl()).thenReturn(acl2);
    when(userAccessService.userHasAccessToRecord(acl1)).thenReturn(true);
    when(userAccessService.userHasAccessToRecord(acl2)).thenReturn(false);

    // Act
    boolean result = repo.hasAccess(record1, record2);

    // Assert
    assertFalse(result);
}

@Test
void hasAccess_shouldIgnoreRecordsWithoutVersion_andReturnTrue() {
    // Arrange
    RecordMetadata record1 = mock(RecordMetadata.class);
    RecordMetadata record2 = mock(RecordMetadata.class);

    when(record1.hasVersion()).thenReturn(false);
    when(record2.hasVersion()).thenReturn(true);
    when(record2.getAcl()).thenReturn(mock(Acl.class));
    when(userAccessService.userHasAccessToRecord(any())).thenReturn(true);

    // Act
    boolean result = repo.hasAccess(record1, record2);

    // Assert
    assertTrue(result);
}
    
}
