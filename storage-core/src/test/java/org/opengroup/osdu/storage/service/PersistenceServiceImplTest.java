// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;

@ExtendWith(MockitoExtension.class)
public class PersistenceServiceImplTest {

    private static final Integer BATCH_SIZE = 48;
    private static final String MODIFIED_BY = "modifyUser";
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    @Mock
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private IMessageBus pubSubClient;

    @Mock
    private DpsHeaders headers;

    @Mock
    private TenantInfo tenant;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private IFeatureFlag collaborationFeatureFlag;

    @InjectMocks
    private PersistenceServiceImpl sut;

    private List<Record> createdRecords;

    private RecordData recordsData;

    private Acl acl;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setup() {
        this.createdRecords = new ArrayList<>();

        this.acl = new Acl();
        this.acl.setViewers(new String[]{"viewers1", "viewers2"});
        this.acl.setOwners(new String[]{"owners1", "owners2"});
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_persistRecords_when_noExceptionIsThrown() {

        when(collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)).thenReturn(false);

        this.setupRecordRepository(25, 23, 25);

        TransferBatch batch = this.createBatchTransfer();

        this.sut.persistRecordBatch(batch, Optional.empty());

        for (int i = 0; i < BATCH_SIZE; i++) {
            verify(this.cloudStorage)
                    .write(batch.getRecords().toArray(new RecordProcessing[batch.getRecords().size()]));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());

        List<List> capturedDatastoreList = datastoreCaptor.getAllValues();

        assertEquals(1, capturedDatastoreList.size());

        List list1 = capturedDatastoreList.get(0);
        assertEquals(48, list1.size());

        ArgumentCaptor<PubSubInfo[]> pubsubCaptor = ArgumentCaptor.forClass(PubSubInfo[].class);

        verify(this.pubSubClient).publishMessage(eq(this.headers), pubsubCaptor.capture());

        this.assertPubsubInfo(48, pubsubCaptor.getAllValues());
        verify(this.cloudStorage, times(0)).delete(any(RecordMetadata.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_persistRecords_when_noExceptionIsThrown_when_collaborationIsEmptyAndFFIsEnabled() {

        when(collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)).thenReturn(true);

        this.setupRecordRepository(25, 23, 25);

        TransferBatch batch = this.createBatchTransfer();

        this.sut.persistRecordBatch(batch, Optional.empty());

        for (int i = 0; i < BATCH_SIZE; i++) {
            verify(this.cloudStorage)
                    .write(batch.getRecords().toArray(new RecordProcessing[batch.getRecords().size()]));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());

        List<List> capturedDatastoreList = datastoreCaptor.getAllValues();

        assertEquals(1, capturedDatastoreList.size());

        List list1 = capturedDatastoreList.get(0);
        assertEquals(48, list1.size());

        ArgumentCaptor<PubSubInfo[]> pubsubCaptor = ArgumentCaptor.forClass(PubSubInfo[].class);
        ArgumentCaptor<RecordChangedV2[]> recordChangedV2Captor = ArgumentCaptor.forClass(RecordChangedV2[].class);

        verify(this.pubSubClient).publishMessage(eq(Optional.empty()), eq(this.headers), recordChangedV2Captor.capture());
        verify(this.pubSubClient).publishMessage(eq(this.headers), pubsubCaptor.capture());

        this.assertPubsubInfo(48, pubsubCaptor.getAllValues());
        this.assertRecordChangedV2Info(48, recordChangedV2Captor.getAllValues());
        verify(this.cloudStorage, times(0)).delete(any(RecordMetadata.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_persistRecords_when_noExceptionIsThrown_when_collaborationIsPresentAndFFIsEnabled() {

        when(collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)).thenReturn(true);

        this.setupRecordRepository(25, 23, 25);

        TransferBatch batch = this.createBatchTransfer();

        this.sut.persistRecordBatch(batch, COLLABORATION_CONTEXT);

        for (int i = 0; i < BATCH_SIZE; i++) {
            verify(this.cloudStorage)
                    .write(batch.getRecords().toArray(new RecordProcessing[batch.getRecords().size()]));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());

        List<List> capturedDatastoreList = datastoreCaptor.getAllValues();

        assertEquals(1, capturedDatastoreList.size());

        List list1 = capturedDatastoreList.get(0);
        assertEquals(48, list1.size());

        ArgumentCaptor<RecordChangedV2[]> recordChangedV2Captor = ArgumentCaptor.forClass(RecordChangedV2[].class);
        ArgumentCaptor<PubSubInfo[]> pubSubInfoCaptor = ArgumentCaptor.forClass(PubSubInfo[].class);

        verify(this.pubSubClient).publishMessage(eq(COLLABORATION_CONTEXT), eq(this.headers), recordChangedV2Captor.capture());
        verify(this.pubSubClient, never()).publishMessage(eq(this.headers), pubSubInfoCaptor.capture());

        this.assertRecordChangedV2Info(48, recordChangedV2Captor.getAllValues());
        verify(this.cloudStorage, times(0)).delete(any(RecordMetadata.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_notPersistRecords_and_throw500AppException_when_nonDatastoreErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        doThrow(new NullPointerException()).when(this.recordRepository).createOrUpdate(any(), any());

        try {
            this.sut.persistRecordBatch(batch, Optional.empty());
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(500, e.getError().getCode());
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.pubSubClient, times(0)).publishMessage(eq(Optional.empty()), any());
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());
        verify(this.recordRepository, times(1)).batchDelete(any(), any());
        assertSameMetaPassedToCloudStorageDelete(batch);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_notPersistRecords_and_throw413AppException_when_datastoreTooBigEntityErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        when(this.recordRepository.createOrUpdate(any(), any())).thenThrow(new AppException(HttpStatus.SC_REQUEST_TOO_LONG, "Request Too Long", "Metadata request size limit reached!")).thenReturn(null);

        try {
            this.sut.persistRecordBatch(batch, Optional.empty());
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(413, e.getError().getCode());
            assertTrue(e.getError().toString().contains("Request Too Long"));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());
        verify(this.recordRepository, times(1)).batchDelete(any(), any());
        verify(this.pubSubClient, times(0)).publishMessage(any(), any(), anyList());
        assertSameMetaPassedToCloudStorageDelete(batch);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_notPersistRecords_andRollBackStorage_and_throw500AppException_when_datastoreOtherErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        AppException firstException = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "other errors", "error");
        AppException secondException = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "another error", "error");
        doThrow(firstException).when(this.recordRepository).createOrUpdate(any(), any());
        doThrow(secondException).when(this.recordRepository).batchDelete(any(), any());

        try {
            this.sut.persistRecordBatch(batch, Optional.empty());
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(500, e.getError().getCode());
            assertEquals(Arrays.stream(e.getSuppressed()).findFirst().get(), secondException);
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture(), any());
        verify(this.recordRepository, times(1)).batchDelete(any(), any());
        verify(this.pubSubClient, times(0)).publishMessage(any(), any());
        assertSameMetaPassedToCloudStorageDelete(batch);
    }

    @Test
    public void should_LogError_AndThrowException_whenDataStoreTooBigEntityErrorOccur() {
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();

        List<String> recordsId = new ArrayList<>();
        recordsId.add("id:access:1");
        recordsId.add("id:access:2");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id:access:1", recordMetadataList.get(0));
        currentRecords.put("id:access:2", recordMetadataList.get(1));

        doThrow(new AppException(HttpStatus.SC_REQUEST_TOO_LONG, "entity is too big", "error")).when(this.recordRepository).createOrUpdate(any(), any());

        try {
            this.sut.updateMetadata(recordMetadataList, recordsId, new HashMap<>(), Optional.empty());
            fail("expected exception");
        } catch (AppException e) {
            assertEquals(413, e.getError().getCode());
            assertTrue(e.getError().toString().contains("entity is too big"));
            verify(this.logger, times(1)).warning("Reverting meta data changes");
        }

        verify(this.recordRepository, times(1)).createOrUpdate(any(), any());
        verify(this.pubSubClient, times(0)).publishMessage(any(), any());
        verify(this.cloudStorage, times(1)).updateObjectMetadata(any(), any(), any(), any(), any(), any());
        verify(this.cloudStorage, times(1)).revertObjectMetadata(any(), any(), any());
    }

    @Test
    public void should_LogError_andThrowException_whenDataStoreOtherErrorOccur() {
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();

        List<String> recordsId = new ArrayList<>();
        recordsId.add("id:access:1");
        recordsId.add("id:access:2");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id:access:1", recordMetadataList.get(0));
        currentRecords.put("id:access:2", recordMetadataList.get(1));

        doThrow(new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "other errors", "error")).when(this.recordRepository).createOrUpdate(any(), any());

        try {
            this.sut.updateMetadata(recordMetadataList, recordsId, new HashMap<>(), Optional.empty());
            fail("expected exception");
        } catch (AppException e) {
            verify(this.logger, times(1)).warning("Reverting meta data changes");
        }
    }

    @Test
    public void should_updateValidRecordsMetadata_whenNoErrorOccurs() {
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();

        List<String> recordsId = new ArrayList<>();
        recordsId.add("id:access:1");
        recordsId.add("id:access:2");

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id:access:1", recordMetadataList.get(0));
        currentRecords.put("id:access:2", recordMetadataList.get(1));

        List<String> result = this.sut.updateMetadata(recordMetadataList, recordsId, new HashMap<>(), Optional.empty());

        assertEquals(0, result.size());
    }

    @Test
    public void should_throwException_whenDatastoreErrorOccurs() throws IOException {
        doThrow(new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "other errors", "error")).when(this.recordRepository).patch(anyMap(), any(Optional.class));
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();
        JsonPatch jsonPatchInput = getJsonPatchFromJsonString(getValidInputJsonForPatch());
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        for (RecordMetadata metadata : recordMetadataList)
            jsonPatchPerRecord.put(metadata, jsonPatchInput);
        try {
            this.sut.patchRecordsMetadata(jsonPatchPerRecord, Optional.empty());
            fail("expected exception");
        } catch (AppException e) {
            verify(this.logger, times(1)).warning("Reverting meta data changes");
            verify(recordRepository, times(1)).createOrUpdate(anyList(), eq(Optional.empty()));
            PubSubInfo[] pubSubInfos = new PubSubInfo[recordMetadataList.size()];
            for (int i = 0; i < recordMetadataList.size(); i++) {
                pubSubInfos[i] = getPubSubInfo(recordMetadataList.get(i));
            }
            verify(pubSubClient, never()).publishMessage(headers, pubSubInfos);
        }
    }

    @Test
    public void should_returnErrors_whenDatastoreErrorOccurs() throws IOException {
        Map<String, String> patchErrors = new HashMap<>();
        patchErrors.put("id123", "basic error");
        when(this.recordRepository.patch(anyMap(), any(Optional.class))).thenReturn(patchErrors);
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();
        JsonPatch jsonPatchInput = getJsonPatchFromJsonString(getValidInputJsonForPatch());
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        for (RecordMetadata metadata : recordMetadataList)
            jsonPatchPerRecord.put(metadata, jsonPatchInput);
        Map<String, String> response = this.sut.patchRecordsMetadata(jsonPatchPerRecord, Optional.empty());
        verify(this.logger, times(1)).warning("Reverting meta data changes");
        PubSubInfo[] pubSubInfos = new PubSubInfo[recordMetadataList.size()];
        for (int i = 0; i < recordMetadataList.size(); i++) {
            pubSubInfos[i] = getPubSubInfo(recordMetadataList.get(i));
        }
        verify(pubSubClient, never()).publishMessage(headers, pubSubInfos);
        verify(recordRepository, times(1)).createOrUpdate(anyList(), eq(Optional.empty()));
        assertTrue(response.containsKey("id123"));
        assertTrue(response.get("id123").equals("basic error"));
    }

    @Test
    public void should_patchRecords_whenNoErrorOccurs_withoutKindUpdate() throws IOException {
        Map<String, String> patchErrors = new HashMap<>();
        when(recordRepository.patch(anyMap(), any(Optional.class))).thenReturn(patchErrors);
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();
        JsonPatch jsonPatchInput = getJsonPatchFromJsonString(getValidInputJsonForPatch());
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        for (RecordMetadata metadata : recordMetadataList)
            jsonPatchPerRecord.put(metadata, jsonPatchInput);
        patchErrors = this.sut.patchRecordsMetadata(jsonPatchPerRecord, Optional.empty());
        verify(pubSubClient, times(1)).publishMessage(eq(headers), any(PubSubInfo[].class));
        assertTrue(patchErrors.isEmpty());
    }

    @Test
    public void should_patchRecords_whenNoErrorOccurs_withKindUpdate() throws IOException {
        Map<String, String> patchErrors = new HashMap<>();
        when(recordRepository.patch(anyMap(), any(Optional.class))).thenReturn(patchErrors);
        List<RecordMetadata> recordMetadataList = this.createListOfRecordMetadata();
        JsonPatch jsonPatchInput = getJsonPatchFromJsonString(getValidInputJsonForPatchKindUpdate());
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        for (RecordMetadata metadata : recordMetadataList)
            jsonPatchPerRecord.put(metadata, jsonPatchInput);
        patchErrors = this.sut.patchRecordsMetadata(jsonPatchPerRecord, Optional.empty());
        verify(pubSubClient, times(1)).publishMessage(eq(headers), any(PubSubInfo[].class));
        assertTrue(patchErrors.isEmpty());
    }

    private PubSubInfo getPubSubInfo(RecordMetadata recordMetadata) {
        return PubSubInfo.builder()
                .id(recordMetadata.getId())
                .kind(recordMetadata.getKind())
                .op(OperationType.update)
                .build();
    }

    private JsonPatch getJsonPatchFromJsonString(String jsonString) throws IOException {
        final InputStream in = new ByteArrayInputStream(jsonString.getBytes());
        return mapper.readValue(in, JsonPatch.class);
    }

    private String getValidInputJsonForPatch() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"add\",\n" +
                "        \"path\": \"/tags\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

    private String getValidInputJsonForPatchKindUpdate() {
        return "[\n" +
                "    {\n" +
                "         \"op\": \"replace\",\n" +
                "         \"path\": \"/kind\",\n" +
                "         \"value\": \"newKind\"\n" +
                "    }\n" +
                "]";
    }

    @SuppressWarnings("unchecked")
    private void setupRecordRepository(int batch1Size, int batch2Size, int idStartPoint) {
        List<Record> entities1 = new ArrayList<>();
        List<Record> entities2 = new ArrayList<>();
        for (int i = 0; i < batch1Size; i++) {
            Record mock = mock(Record.class);
            entities1.add(mock);
        }

        for (int i = 0; i < batch2Size; i++) {
            Record mock = mock(Record.class);
            entities2.add(mock);
        }

        lenient().when(this.recordRepository.createOrUpdate(any(List.class), any())).thenReturn(entities1, entities2);
    }

    private TransferBatch createBatchTransfer() {
        TransferInfo transferInfo = new TransferInfo();
        transferInfo.setRecordCount(BATCH_SIZE);
        transferInfo.setVersion(123456L);
        transferInfo.setUser("transactionUser");

        List<RecordProcessing> recordsProcessing = new ArrayList<>();

        for (int i = 0; i < BATCH_SIZE; i++) {

            List<String> acls = new ArrayList<>();
            acls.add("anyAcl");

            Record record = new Record();
            record.setId("ID" + i);
            record.setKind("anyKind");
            record.setAcl(this.acl);

            Map<String, Object> data = new HashMap<String, Object>();
            data.put("name", "test");
            record.setData(data);

            this.recordsData = new RecordData(record);

            RecordMetadata recordMetadata = new RecordMetadata();
            recordMetadata.setId("ID" + i);
            recordMetadata.setKind("anyKind");
            recordMetadata.setAcl(this.acl);
            recordMetadata.setUser("createUser");
            recordMetadata.setModifyUser("modifyUser");
            Date date = new Date();
            recordMetadata.setGcsVersionPaths(Arrays.asList(String.format("%s/%s/%s", "anyKind", ("ID" + i), date.getTime())));

            RecordProcessing processing = new RecordProcessing();
            // Create and update operations
            processing.setOperationType(i % 2 == 0 ? OperationType.create : OperationType.update);

            processing.setRecordMetadata(recordMetadata);
            processing.setRecordData(this.recordsData);

            recordsProcessing.add(processing);

            this.createdRecords.add(record);

        }

        return new TransferBatch(transferInfo, recordsProcessing);
    }

    @SuppressWarnings("unchecked")
    private void assertPubsubInfo(int successfullRecords, Object capturedPubsubList) {
        List<PubSubInfo[]> pubsubList = (ArrayList<PubSubInfo[]>) capturedPubsubList;
        // Captured variable arguments are inside an ArrayList of size 1
        // if you find a better way to capture them, feel free to improve
        assertEquals(1, pubsubList.size());
        PubSubInfo[] innerList = pubsubList.get(0);

        assertEquals(successfullRecords, innerList.length);
        for (int i = 0; i < innerList.length; i++) {
            PubSubInfo pubSubInfo = innerList[i];
            assertEquals("anyKind", pubSubInfo.getKind());
            assertEquals(i % 2 == 0 ? OperationType.create : OperationType.update, pubSubInfo.getOp());
            assertNull(pubSubInfo.getPreviousVersionKind());
            assertTrue(pubSubInfo.getId().startsWith("ID"));
        }
    }

    private void assertRecordChangedV2Info(int successfullRecords, Object capturedRecordChangedV2List) {

        List<RecordChangedV2[]> recordChangedV2s = (ArrayList<RecordChangedV2[]>) capturedRecordChangedV2List;
        // Captured variable arguments are inside an ArrayList of size 1
        // if you find a better way to capture them, feel free to improve
        assertEquals(1, recordChangedV2s.size());
        RecordChangedV2[] innerList = recordChangedV2s.get(0);

        assertEquals(successfullRecords, innerList.length);

        for (int i = 0; i < innerList.length; i++) {
            RecordChangedV2 recordChangedV2 = innerList[i];
            assertEquals("anyKind", recordChangedV2.getKind());
            assertEquals(i % 2 == 0 ? OperationType.create : OperationType.update, recordChangedV2.getOp());
            assertNull(recordChangedV2.getPreviousVersionKind());
            assertTrue(recordChangedV2.getId().startsWith("ID"));
            assertNotNull(recordChangedV2.getVersion());
            assertEquals(MODIFIED_BY, recordChangedV2.getModifiedBy());
        }
    }

    private List<RecordMetadata> createListOfRecordMetadata() {
        List<RecordMetadata> recordMetadataList = new ArrayList<>();

        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setId("id:access:1");
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        RecordMetadata record2 = new RecordMetadata();
        record2.setKind("any kind");
        record2.setId("id:access:2");
        record2.setStatus(RecordState.active);
        record2.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        recordMetadataList.add(record);
        recordMetadataList.add(record2);

        return recordMetadataList;
    }

    private void assertSameMetaPassedToCloudStorageDelete(TransferBatch batch) {
        ArgumentCaptor<RecordMetadata> metadataArgumentCaptor = ArgumentCaptor.forClass(RecordMetadata.class);
        ArgumentCaptor<Long> versionArgumentCaptor = ArgumentCaptor.forClass(Long.class);

        verify(this.cloudStorage, times(48)).deleteVersion(metadataArgumentCaptor.capture(), versionArgumentCaptor.capture());

        List<RecordMetadata> expectedMeta = batch.getRecords().stream()
            .map(RecordProcessing::getRecordMetadata)
            .toList();
        List<Long> expectedVersions = expectedMeta.stream()
            .map(RecordMetadata::getLatestVersion)
            .toList();

        assertEquals(expectedMeta, metadataArgumentCaptor.getAllValues());
        assertEquals(expectedVersions, versionArgumentCaptor.getAllValues());
    }
}
