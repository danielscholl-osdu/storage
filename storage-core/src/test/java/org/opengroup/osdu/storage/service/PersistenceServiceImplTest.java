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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
//import com.google.cloud.datastore.DatastoreException;
import org.apache.http.HttpStatus;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PersistenceServiceImplTest {

    private static final Integer BATCH_SIZE = 48;
    private static final String BUCKET = "anyBucket";

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

    @InjectMocks
    private PersistenceServiceImpl sut;

    private List<Record> createdRecords;

    private RecordData recordsData;

    private Acl acl;

    @Before
    public void setup() {
        this.createdRecords = new ArrayList<>();

        this.acl = new Acl();
        this.acl.setViewers(new String[] { "viewers1", "viewers2" });
        this.acl.setOwners(new String[] { "owners1", "owners2" });
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void should_persistRecords_when_noExceptionIsThrown() {

        this.setupRecordRepository(25, 23, 25);

        TransferBatch batch = this.createBatchTransfer();

        this.sut.persistRecordBatch(batch);

        for (int i = 0; i < BATCH_SIZE; i++) {
            verify(this.cloudStorage)
                    .write(batch.getRecords().toArray(new RecordProcessing[batch.getRecords().size()]));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture());

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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void should_notPersistRecords_and_throw500AppException_when_nonDatastoreErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        doThrow(new NullPointerException()).when(this.recordRepository).createOrUpdate(any());

        try {
            this.sut.persistRecordBatch(batch);
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(500, e.getError().getCode());
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture());
        verify(this.pubSubClient, times(0)).publishMessage(any());
    }
    @Ignore
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_notPersistRecords_and_throw413AppException_when_datastoreTooBigEntityErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        doThrow(new AppException(HttpStatus.SC_REQUEST_TOO_LONG, "entity is too big", "error")).when(this.recordRepository).createOrUpdate(any());
        try {
            this.sut.persistRecordBatch(batch);
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(413, e.getError().getCode());
            assertTrue(e.getError().toString().contains("The record metadata is too big"));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture());
        verify(this.pubSubClient, times(0)).publishMessage(any());
    }
    @Ignore
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void should_notPersistRecords_and_throw500AppException_when_datastoreOtherErrorOccur() {

        TransferBatch batch = this.createBatchTransfer();

        this.setupRecordRepository(23, 10, 25);
        doThrow(new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "other errors", "error")).when(this.recordRepository).createOrUpdate(any());
        try {
            this.sut.persistRecordBatch(batch);
            fail("Expected exception");
        } catch (AppException e) {
            assertEquals(500, e.getError().getCode());
            assertTrue(e.getError().getMessage().contains("The server could not write metadata to Datastore at the moment"));
        }

        ArgumentCaptor<List> datastoreCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository, times(1)).createOrUpdate(datastoreCaptor.capture());
        verify(this.pubSubClient, times(0)).publishMessage(any());
    }

    @SuppressWarnings("unchecked")
    private void setupRecordRepository(int batch1Size, int batch2Size, int idStartPoint) {
        List<Record> entities1 = new ArrayList<>();
        List<Record> entities2 = new ArrayList<>();
        for (int i = 0; i < batch1Size; i++) {
            Record mock = mock(Record.class);
            when(mock.getId()).thenReturn("ID" + i);
            entities1.add(mock);
        }

        for (int i = 0; i < batch2Size; i++) {
            Record mock = mock(Record.class);
            when(mock.getId()).thenReturn("ID" + (i + idStartPoint));
            entities2.add(mock);
        }

        when(this.recordRepository.createOrUpdate(any(List.class))).thenReturn(entities1, entities2);
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

        LinkedList<PubSubInfo> pubsubList = (LinkedList<PubSubInfo>) capturedPubsubList;

        assertEquals(successfullRecords, pubsubList.size());

        for (PubSubInfo pubSubInfo : pubsubList) {
            assertEquals("anyKind", pubSubInfo.getKind());
            assertEquals(OperationType.create, pubSubInfo.getOp());
            assertTrue(pubSubInfo.getId().startsWith("ID"));
        }
    }
}