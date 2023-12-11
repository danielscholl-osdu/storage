package org.opengroup.osdu.storage.provider.azure;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.blobstorage.BlobStore;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.provider.azure.repository.RecordMetadataRepository;
import org.opengroup.osdu.storage.provider.azure.util.EntitlementsHelper;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.util.CrcHashGenerator;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudStorageImplTest {

    private static final String DATA_PARTITION = "dp";
    private static final String CONTAINER = "opendes";
    private static final CrcHashGenerator crcHashGenerator = new CrcHashGenerator();
    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private DpsHeaders headers;
    @Mock
    private BlobStore blobStore;
    @Mock
    private RecordMetadataRepository recordRepository;
    @Mock
    private EntitlementsHelper entitlementsHelper;
    @Mock
    private RecordProcessing recordProcessing;
    @Mock
    private RecordUtil recordUtil;
    @Mock
    private RecordData data;
    @Mock
    private TransferInfo transfer;
    @InjectMocks
    private CloudStorageImpl cloudStorage;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cloudStorage, "containerName", CONTAINER);
    }

    @Test
    void shouldNotInvokeDeleteOnBlobStoreWhenNoVersion() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        cloudStorage.delete(recordMetadata);
        verify(blobStore, Mockito.never()).deleteFromStorageContainer(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void shouldNotInvokeDeleteOnBlobStoreWhenReferencedFromOtherDocuments() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(List.of("path1"));
        when(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata)).thenReturn(true);
        when(recordRepository.getMetadataDocumentCountForBlob("path1")).thenReturn(1);
        cloudStorage.delete(recordMetadata);
        verify(blobStore, Mockito.never()).deleteFromStorageContainer(any(String.class), any(String.class), any(String.class));
    }

    @Test
    void shouldThrowAppExceptionWhenDeleteWithNoOwnerAccess() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(Arrays.asList("path1", "path2"));
        when(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata)).thenReturn(false);

        AppException exception = assertThrows(AppException.class, () -> cloudStorage.delete(recordMetadata));

        validateAccessDeniedException(exception);
    }

    @Test
    void shouldDeleteAllVersionsFromBlobStoreUponDeleteAction() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(Arrays.asList("path1", "path2"));

        when(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata)).thenReturn(true);
        when(recordRepository.getMetadataDocumentCountForBlob("path1")).thenReturn(0);
        when(recordRepository.getMetadataDocumentCountForBlob("path2")).thenReturn(0);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);

        cloudStorage.delete(recordMetadata);

        verify(blobStore, Mockito.times(1)).deleteFromStorageContainer(DATA_PARTITION, "path1", CONTAINER);
        verify(blobStore, Mockito.times(1)).deleteFromStorageContainer(DATA_PARTITION, "path2", CONTAINER);
    }

    @Test
    void shouldWriteToBlob_when_writeIsCalled() {
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        ReflectionTestUtils.setField(cloudStorage, "threadPool", executorService);

        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(Collections.singletonList("path/1"));
        RecordData recordData = createAndGetRandomRecordData();

        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordProcessing.getRecordData()).thenReturn(recordData);
        when(recordProcessing.getRecordMetadata()).thenReturn(recordMetadata);

        cloudStorage.write(recordProcessing);

        Gson gson = new GsonBuilder().serializeNulls().create();
        verify(blobStore, Mockito.times(1)).writeToStorageContainer(DATA_PARTITION, "kind/id1/1", gson.toJson(recordData), CONTAINER);
    }

    @Test
    void updateObjectMetadata_updatesWhenIdsMatch() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        List<RecordMetadata> recordMetadataList = Collections.singletonList(recordMetadata);

        List<String> recordsId = Collections.singletonList("data-partition:record1:version:1");
        Map<String, String> recordsIdMap = new HashMap<>();
        recordsIdMap.put("id1", "data-partition:record1:version:1");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id1", recordMetadata);

        when(recordRepository.get(recordsId, Optional.empty())).thenReturn(currentRecords);
        recordMetadata.setGcsVersionPaths(Arrays.asList("path/0", "path/1"));
        List<RecordMetadata> validMetaData = new ArrayList<>();
        // Execution
        Map<String, Acl> originalAcls = cloudStorage.updateObjectMetadata(recordMetadataList, recordsId, validMetaData, new ArrayList<>(), recordsIdMap, Optional.empty());

        // Verification
        assertEquals(1, originalAcls.size());
        assertEquals(1, validMetaData.size());
    }

    @Test
    void updateObjectMetadata_updatesWhenIdsDontMatch() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        List<RecordMetadata> recordMetadataList = Collections.singletonList(recordMetadata);

        List<String> recordsId = Collections.singletonList("data-partition:record1:version:1");
        Map<String, String> recordsIdMap = new HashMap<>();
        recordsIdMap.put("id1", "data-partition:record1:version:1");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id1", recordMetadata);

        when(recordRepository.get(recordsId, Optional.empty())).thenReturn(currentRecords);
        recordMetadata.setGcsVersionPaths(Arrays.asList("path/1", "path/2"));
        List<String> lockedRecords = new ArrayList<>();
        List<RecordMetadata> validMetaData = new ArrayList<>();
        // Execution
        Map<String, Acl> originalAcls = cloudStorage.updateObjectMetadata(recordMetadataList, recordsId, validMetaData, lockedRecords, recordsIdMap, Optional.empty());

        // Verification
        assertEquals(0, originalAcls.size());
        assertEquals(0, validMetaData.size());
        assertEquals(1, lockedRecords.size());
    }

    @Test
    void readWithVersion_isSuccessful() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(List.of("path1"));
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordUtil.getKindForVersion(recordMetadata, "1")).thenReturn("kind");

        cloudStorage.read(recordMetadata, 1L, true);

        verify(blobStore, Mockito.times(1)).readFromStorageContainer(DATA_PARTITION, "kind/id1/1", CONTAINER);
    }

    @Test
    void testReadWithVersion_recoversObject_whenDataInconsistencyIsFound() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(List.of("path1"));

        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordUtil.getKindForVersion(recordMetadata, "1")).thenReturn("kind");

        when(blobStore.readFromStorageContainer(DATA_PARTITION, "kind/id1/1", CONTAINER)).thenThrow(new AppException(HttpStatus.SC_NOT_FOUND, "NotFound", "NotFound")).thenReturn("some content");

        long version = 1;
        cloudStorage.read(recordMetadata, version, true);

        verify(blobStore, Mockito.times(1)).undeleteFromStorageContainer(DATA_PARTITION, "kind/id1/1", CONTAINER);
        verify(blobStore, Mockito.times(2)).readFromStorageContainer(DATA_PARTITION, "kind/id1/1", CONTAINER);
    }

    @Test
    void testReadWithVersion_fails_whenOwnerAndViewerPrivilegesNotPresent() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setGcsVersionPaths(List.of("path1"));

        when(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata)).thenReturn(false);
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(false);

        long version = 1;
        AppException exception = assertThrows(AppException.class, () ->
                cloudStorage.read(recordMetadata, version, true));

        validateAccessDeniedException(exception);
    }

    @Test
    void revertObjectMetadata_isSuccessful_whenAllRequirementsAreMet() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls = new HashMap<>();
        Acl acl = Acl.builder()
                .viewers(new String[]{"original viewers"})
                .owners(new String[]{"original owners"})
                .build();
        originalAcls.put("id1", acl);

        List<RecordMetadata> recordMetadataList = Collections.singletonList(recordMetadata);
        cloudStorage.revertObjectMetadata(recordMetadataList, originalAcls, Optional.empty());

        assertEquals(originalAcls.get("id1"), recordMetadataList.get(0).getAcl());
        recordMetadata.setAcl(acl);
        List<RecordMetadata> originalRecordMetadata = Collections.singletonList(recordMetadata);

        verify(recordRepository, times(1)).createOrUpdate(originalRecordMetadata, Optional.empty());
    }

    @Test
    void revertObjectMetadataThrowsInternalServerException_when_recordRepositoryUpdateFails() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls = new HashMap<>();
        Acl acl = Acl.builder()
                .viewers(new String[]{"original viewers"})
                .owners(new String[]{"original owners"})
                .build();
        originalAcls.put("id1", acl);

        List<RecordMetadata> recordMetadataList = Collections.singletonList(recordMetadata);
        when(recordRepository.createOrUpdate(any(), any())).
                thenThrow(RuntimeException.class);

        Optional<CollaborationContext> context = Optional.empty();
        AppException exception = assertThrows(AppException.class, () ->
                cloudStorage.revertObjectMetadata(recordMetadataList, originalAcls, context));

        assertEquals(originalAcls.get("id1"), recordMetadataList.get(0).getAcl());
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
    }

    @Test
    void shouldReadSuccessfullyFromBlobstore_when_readRecords() {
        Map<String, String> objects = new HashMap<>();
        objects.put("id1", "path1");
        objects.put("id2", "path2");
        Map<String, RecordMetadata> recordMetadatarecordMetadataMap = new HashMap<>();
        recordMetadatarecordMetadataMap.put("id1", setUpRecordMetadata("id1"));
        recordMetadatarecordMetadataMap.put("id2", setUpRecordMetadata("id2"));
        when(recordRepository.get(objects.keySet().stream().toList(), Optional.empty()))
                .thenReturn(recordMetadatarecordMetadataMap);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(entitlementsHelper.hasViewerAccessToRecord(any())).thenReturn(Boolean.TRUE);
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "path1", CONTAINER)).thenReturn("content1");
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "path2", CONTAINER)).thenReturn("content2");
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        ReflectionTestUtils.setField(cloudStorage, "threadPool", executorService);

        Map<String, String> recordIdContentMap = cloudStorage.read(objects, Optional.empty());

        verify(blobStore, times(1))
                .readFromStorageContainer(DATA_PARTITION, "path1", CONTAINER);
        verify(blobStore, times(1))
                .readFromStorageContainer(DATA_PARTITION, "path2", CONTAINER);

        assertEquals(2, recordIdContentMap.size());
    }

    @Test
    void shouldAttemptRestoreBlob_when_blobStoreReadThrowsException() {
        Map<String, String> objectsToRead = new HashMap<>();
        objectsToRead.put("id1", "path1");
        objectsToRead.put("id2", "path2");
        Map<String, RecordMetadata> recordMetadatarecordMetadataMap = new HashMap<>();
        recordMetadatarecordMetadataMap.put("id1", setUpRecordMetadata("id1"));
        recordMetadatarecordMetadataMap.put("id2", setUpRecordMetadata("id2"));
        when(recordRepository.get(objectsToRead.keySet().stream().toList(), Optional.empty()))
                .thenReturn(recordMetadatarecordMetadataMap);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(entitlementsHelper.hasViewerAccessToRecord(any())).thenReturn(Boolean.TRUE);

        when(blobStore.readFromStorageContainer(DATA_PARTITION, "path1", CONTAINER)).thenReturn("content1");
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "path2", CONTAINER)).thenThrow(new AppException(HttpStatus.SC_NOT_FOUND, "NotFound", "NotFound")).thenReturn("content2");

        ExecutorService executorService = Executors.newFixedThreadPool(3);
        ReflectionTestUtils.setField(cloudStorage, "threadPool", executorService);

        Map<String, String> recordIdContentMap = cloudStorage.read(objectsToRead, Optional.empty());

        verify(blobStore, times(1))
                .readFromStorageContainer(DATA_PARTITION, "path1", CONTAINER);
        verify(blobStore, times(2))
                .readFromStorageContainer(DATA_PARTITION, "path2", CONTAINER);
        verify(blobStore).undeleteFromStorageContainer(DATA_PARTITION, "path2", CONTAINER);
        assertEquals(2, recordIdContentMap.size());
    }

    @Test
    void hasAccessReturnsTrue_when_accessToAllRecordsIsPresent() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");
        recordMetadata.setStatus(RecordState.active);
        recordMetadata2.setStatus(RecordState.active);

        recordMetadata.setGcsVersionPaths(List.of("1"));
        recordMetadata2.setGcsVersionPaths(List.of("2"));

        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);

        boolean access = cloudStorage.hasAccess(recordMetadata, recordMetadata2);

        assertTrue(access);
    }

    @Test
    void hasAccessReturnsTrue_when_accessToOneActiveRecordIsPresent() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");
        recordMetadata.setStatus(RecordState.active);
        recordMetadata2.setStatus(RecordState.deleted);

        recordMetadata.setGcsVersionPaths(List.of("1"));
        recordMetadata2.setGcsVersionPaths(List.of("2"));

        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);

        boolean access = cloudStorage.hasAccess(recordMetadata, recordMetadata2);

        assertTrue(access);
    }

    @Test
    void hasAccessReturnsFalse_when_accessToAnyRecordIsNotPresent() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        RecordMetadata recordMetadata2 = setUpRecordMetadata("id2");
        recordMetadata.setStatus(RecordState.active);
        recordMetadata2.setStatus(RecordState.active);

        recordMetadata.setGcsVersionPaths(List.of("1"));
        recordMetadata2.setGcsVersionPaths(List.of("2"));

        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(false);
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata2)).thenReturn(false);

        boolean access = cloudStorage.hasAccess(recordMetadata, recordMetadata2);

        assertFalse(access);
    }

    @Test
    void hasAccess_returnsTrue_whenNoRecordsPresent() {
        boolean access = cloudStorage.hasAccess();

        assertTrue(access);
    }

    @Test
    void hasAccess_returnsTrue_whenRecordsAreDeleted() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setStatus(RecordState.deleted);

        boolean access = cloudStorage.hasAccess(recordMetadata);

        assertTrue(access);
    }

    @Test
    void hasAccess_returnsTrue_whenRecordsHaveNoVersion() {
        RecordMetadata recordMetadata = setUpRecordMetadata("id1");
        recordMetadata.setStatus(RecordState.active);

        boolean access = cloudStorage.hasAccess(recordMetadata);

        assertTrue(access);
    }

    @Test
    void deleteVersionIsSuccess_when_ownerAccessIsPresent() {
        RecordMetadata recordMeta = setUpRecordMetadata("recordId");
        recordMeta.setGcsVersionPaths(List.of("1"));
        when(entitlementsHelper.hasOwnerAccessToRecord(recordMeta)).thenReturn(true);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordUtil.getKindForVersion(recordMeta, "1")).thenReturn("kind");

        cloudStorage.deleteVersion(recordMeta, 1L);

        verify(blobStore, times(1)).deleteFromStorageContainer(DATA_PARTITION, "kind/recordId/1", CONTAINER);
    }


    @Test
    void getHash_returnsHashForACollection() {
        String recordId = "recordId";
        RecordData recordData = createAndGetRandomRecordData();
        RecordMetadata recordMetadata = setUpRecordMetadata(recordId);
        recordMetadata.setGcsVersionPaths(List.of("1"));
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);
        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordUtil.getKindForVersion(recordMetadata, "1")).thenReturn("kind");


        Gson gson = new GsonBuilder().serializeNulls().create();
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "kind/recordId/1", CONTAINER)).thenReturn(gson.toJson(recordData));

        Map<String, String> expectedHashes = new HashMap<>();
        expectedHashes.put(recordId, crcHashGenerator.getHash(recordData));
        ReflectionTestUtils.setField(cloudStorage, "crcHashGenerator", crcHashGenerator);

        assertEquals(expectedHashes, cloudStorage.getHash(List.of(recordMetadata)));
    }

    @Test
    void getHash_skipsRecord_whenExceptionOccursForOneItemInList() {
        String recordId = "recordId";
        RecordData recordData = createAndGetRandomRecordData();
        RecordMetadata recordMetadata = setUpRecordMetadata(recordId);
        recordMetadata.setGcsVersionPaths(List.of("1"));

        RecordMetadata recordMetadata2 = setUpRecordMetadata("recordId2");
        recordMetadata2.setGcsVersionPaths(List.of("1"));
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata)).thenReturn(true);
        when(entitlementsHelper.hasViewerAccessToRecord(recordMetadata2)).thenReturn(true);

        when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
        when(recordUtil.getKindForVersion(recordMetadata, "1")).thenReturn("kind");
        when(recordUtil.getKindForVersion(recordMetadata2, "1")).thenReturn("kind");


        Gson gson = new GsonBuilder().serializeNulls().create();
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "kind/recordId/1", CONTAINER)).thenReturn(gson.toJson(recordData));

        //return non-compliant data for another record
        when(blobStore.readFromStorageContainer(DATA_PARTITION, "kind/recordId2/1", CONTAINER)).thenReturn(recordData.toString());

        Map<String, String> expectedHashes = new HashMap<>();
        //only one hash in expected output
        expectedHashes.put(recordId, crcHashGenerator.getHash(recordData));
        ReflectionTestUtils.setField(cloudStorage, "crcHashGenerator", crcHashGenerator);

        assertEquals(expectedHashes, cloudStorage.getHash(Arrays.asList(recordMetadata, recordMetadata2)));
    }

    @Test
    void isDuplicated_returnsTrue_whenDuplicatedRecordsFound() {
        Map<String, String> hashMap = new HashMap<>();
        RecordData recordData = createAndGetRandomRecordData();
        RecordMetadata recordMetadata = setUpRecordMetadata("recordId");

        Map.Entry<RecordMetadata, RecordData> kv = new AbstractMap.SimpleEntry<>(recordMetadata, recordData);

        List<String> skippedRecords = new ArrayList<>();
        when(transfer.getSkippedRecords()).thenReturn(skippedRecords);
        ReflectionTestUtils.setField(cloudStorage, "crcHashGenerator", crcHashGenerator);
        hashMap.put("recordId", crcHashGenerator.getHash(recordData));

        boolean result = cloudStorage.isDuplicateRecord(transfer, hashMap, kv);

        assertTrue(result);
        assertEquals(1, transfer.getSkippedRecords().size());
    }

    @Test
    void isDuplicated_returnsFalse_whenDuplicatedRecordsNotFound() {
        Map<String, String> hashMap = new HashMap<>();
        RecordData recordData = createAndGetRandomRecordData();
        RecordMetadata recordMetadata = setUpRecordMetadata("recordId");

        Map.Entry<RecordMetadata, RecordData> kv = new AbstractMap.SimpleEntry<>(recordMetadata, recordData);

        List<String> skippedRecords = new ArrayList<>();
        when(transfer.getSkippedRecords()).thenReturn(skippedRecords);
        ReflectionTestUtils.setField(cloudStorage, "crcHashGenerator", crcHashGenerator);
        //different record hash
        hashMap.put("recordId", crcHashGenerator.getHash(recordData.toString()));

        boolean result = cloudStorage.isDuplicateRecord(transfer, hashMap, kv);

        assertFalse(result);
        assertEquals(0, transfer.getSkippedRecords().size());
    }

    private RecordMetadata setUpRecordMetadata(String id) {
        Record record = new Record();
        record.setId(id);
        Acl acl = Acl.builder()
                .viewers(new String[]{"viewers"})
                .owners(new String[]{"owners"})
                .build();
        record.setAcl(acl);
        record.setKind("kind");
        Legal legal = new Legal();
        legal.setStatus(LegalCompliance.compliant);
        legal.setLegaltags(Sets.newHashSet("legalTag1"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("US"));
        record.setLegal(legal);
        record.setVersion(1L);
        RecordMetadata recordMetadata = new RecordMetadata(record);
        return recordMetadata;
    }

    private RecordData createAndGetRandomRecordData() {
        RecordData recordData = new RecordData();
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        recordData.setData(data);
        recordData.setMeta(new Map[]{data});
        recordData.setModifyTime(123L);
        recordData.setModifyUser("user");
        return recordData;
    }

    private void validateAccessDeniedException(AppException exception) {
        assertNotNull(exception);
        assertEquals(403, exception.getError().getCode());
        assertEquals("The user is not authorized to perform this action", exception.getError().getMessage());
    }
}
