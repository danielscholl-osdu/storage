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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;

import java.security.Timestamp;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IngestionServiceImplTest {

    @Mock
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private IPersistenceService persistenceService;

    @Mock
    private ILegalService legalService;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private DpsHeaders headers;

    @Mock
    private TenantInfo tenant;

    @Mock
    private ITenantFactory tenantFactory;

    @Mock
    private IEntitlementsAndCacheService authService;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private IngestionServiceImpl sut;

    private static final String RECORD_ID1 = "tenant1:kind:record1";
    private static final String RECORD_ID2 = "tenant1:crazy:record2";
    private static final String KIND_1 = "tenant1:test:kind:1.0.0";
    private static final String KIND_2 = "tenant1:test:crazy:2.0.2";
    private static final String USER = "testuser@gmail.com";
    private static final String NEW_USER = "newuser@gmail.com";
    private static final String TENANT = "tenant1";
    private static final String[] VALID_ACL = new String[] { "data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com" };
    private static final String[] INVALID_ACL = new String[] { "data.email1@test.test.com", "data.test@test.test.com" };

    private Record record1;
    private Record record2;

    private List<Record> records;
    private Acl acl;

    @Before
    public void setup() {

        List<String> userHeaders = new ArrayList<>();
        userHeaders.add(USER);

        this.acl = new Acl();

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record1 = new Record();
        this.record1.setKind(KIND_1);
        this.record1.setId(RECORD_ID1);
        this.record1.setLegal(legal);

        this.record2 = new Record();
        this.record2.setKind(KIND_2);
        this.record2.setId(RECORD_ID2);
        this.record2.setLegal(legal);

        this.records = new ArrayList<>();
        this.records.add(this.record1);
        this.records.add(this.record2);

        this.record1.setAcl(this.acl);
        this.record2.setAcl(this.acl);

        when(this.tenant.getName()).thenReturn(TENANT);
        when(this.headers.getPartitionIdWithFallbackToAccountId()).thenReturn(TENANT);
        when(this.tenantFactory.exists(TENANT)).thenReturn(true);
        when(this.tenantFactory.getTenantInfo(TENANT)).thenReturn(this.tenant);
        when(this.authService.hasOwnerAccess(any(),any())).thenReturn(true);
    }

    @Test
    public void should_throwAppException400_when_updatingSameRecordMoreThanOnceInRequest() {

        final String NEW_RECORD_ID = "tenant1:record:123";

        this.record1.setId(NEW_RECORD_ID);
        this.record1.setKind("tenant1:wks:record:1.0.0");
        this.record2.setId(NEW_RECORD_ID);
        this.record2.setKind("tenant1:wks:record:1.0.0");

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);

        when(this.recordRepository.get(NEW_RECORD_ID)).thenReturn(existingRecordMetadata1);
        when(this.recordRepository.get(NEW_RECORD_ID)).thenReturn(existingRecordMetadata2);

        try {
            this.sut.createUpdateRecords(false, this.records, USER);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Bad request", e.getError().getReason());
            assertEquals("Cannot update the same record multiple times in the same request. Id: tenant1:record:123",
                    e.getError().getMessage());
        }
    }

    @Test
    public void should_throwAppException400_when_recordIdDoesNotFollowTenantNameConvention() {

        final String INVALID_RECORD_ID = "gasguys:record:123";

        this.record1.setId(INVALID_RECORD_ID);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);

        when(this.recordRepository.get(INVALID_RECORD_ID)).thenReturn(existingRecordMetadata1);
        when(this.recordRepository.get(INVALID_RECORD_ID)).thenReturn(existingRecordMetadata2);

        try {
            this.sut.createUpdateRecords(false, this.records, USER);

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid record id", e.getError().getReason());
            assertEquals(
                "The record 'gasguys:record:123' does not follow the naming convention: The record id must be in the format of <tenantId>:<kindSubType>:<uniqueId>. Example: tenant1:kind:<uuid>",
                    e.getError().getMessage());
        }
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void should_createTwoRecords_when_twoRecordsWithoutIdArePersisted() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.record1.setId(null);
        this.record2.setId(null);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        when(this.cloudStorage.hasAccess(new RecordMetadata[] {})).thenReturn(true);

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER);
        assertEquals(new Integer(2), transferInfo.getRecordCount());

        ArgumentCaptor<List> ids = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository).get(ids.capture());

        List<String> capturedIds = ids.getValue();
        assertEquals(2, capturedIds.size());
        assertTrue(capturedIds.get(0).startsWith("tenant1:"));
        assertTrue(capturedIds.get(1).startsWith("tenant1:"));

        ArgumentCaptor<TransferBatch> transferCaptor = ArgumentCaptor.forClass(TransferBatch.class);
        verify(this.persistenceService).persistRecordBatch(transferCaptor.capture());
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch capturedTransfer = transferCaptor.getValue();
        assertEquals(transferInfo, capturedTransfer.getTransferInfo());
        assertEquals(2, capturedTransfer.getRecords().size());

        // TODO ASSERT VALUES ON RECORD
        for (RecordProcessing processing : capturedTransfer.getRecords()) {
            if (processing.getRecordMetadata().getKind().equals(KIND_1)) {
                assertEquals(OperationType.create, processing.getOperationType());
            } else {
                assertEquals(OperationType.create, processing.getOperationType());
            }
        }
    }

    @Test
    public void should_return403_when_updatingARecordThatDoesNotHaveWritePermissionOnOriginalRecord() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata);

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1, RECORD_ID2))).thenReturn(output);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(false);

        try {
            this.sut.createUpdateRecords(false, this.records, USER);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        }
    }

    @Test
    public void should_return403_when_updatingARecordThatDoesNotHaveOwnerAccessOnOriginalRecord() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(this.acl);
        existingRecordMetadata1.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        when(this.cloudStorage.hasAccess(existingRecordMetadata1)).thenReturn(true);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata1);
        when(this.authService.hasValidAccess(any(), any())).thenReturn(recordMetadataList);
        when(this.authService.hasOwnerAccess(any(), any())).thenReturn(false);

        when(this.recordRepository.get(any(List.class))).thenReturn(output);

        try {
            this.sut.createUpdateRecords(false, this.records, USER);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("User Unauthorized", e.getError().getReason());
            assertEquals("User is not authorized to update records.", e.getError().getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_updateTwoRecords_when_twoRecordIDsAreAlreadyPresentInDataLake() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record2.setId(RECORD_ID2);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(this.acl);
        existingRecordMetadata1.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);
        existingRecordMetadata2.setKind(KIND_2);
        existingRecordMetadata2.setStatus(RecordState.active);
        existingRecordMetadata2.setAcl(this.acl);
        existingRecordMetadata2.setGcsVersionPaths(Lists.newArrayList("path/4", "path/5"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);
        output.put(RECORD_ID2, existingRecordMetadata2);

        when(this.cloudStorage.hasAccess(output.values().toArray(new RecordMetadata[output.size()]))).thenReturn(true);

        when(this.recordRepository.get(any(List.class))).thenReturn(output);

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER);
        assertEquals(USER, transferInfo.getUser());
        assertEquals(new Integer(2), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture());
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_disregardUpdateRecord_when_skipDupesAndSameRecordContent() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.records.remove(1);

        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        this.record1.setData(data);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata updatedRecordMetadata = new RecordMetadata(record1);

        List<String> versions = new ArrayList<>();
        versions.add("kind/id/445");
        updatedRecordMetadata.resetGcsPath(versions);

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, updatedRecordMetadata);

        when(this.recordRepository.get(any(List.class))).thenReturn(output);
        when(this.cloudStorage.hasAccess(updatedRecordMetadata)).thenReturn(true);

        Record recordInStorage = new Record();
        recordInStorage.setVersion(3L);
        recordInStorage.setData(data);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);

        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(RECORD_ID1, "vF1SOQ==");

        when(this.cloudStorage.getHash(any())).thenReturn(hashMap);
        when(this.cloudStorage.isDuplicateRecord(any(), eq(hashMap), any())).thenReturn(true);

        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER);
        assertEquals(USER, transferInfo.getUser());
        assertEquals(new Integer(1), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(0)).persistRecordBatch(any());
    }

    @Test
    public void should_considerUpdateRecord_when_skipDupesAndDifferentRecordContent() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.records.remove(1);

        Map<String, Object> data1 = new HashMap<>();
        data1.put("country", "USA");
        data1.put("state", "TX");

        Map<String, Object> data2 = new HashMap<>();
        data2.put("country", "USA");
        data2.put("state", "TN");

        this.record1.setId(RECORD_ID1);
        this.record1.setData(data1);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setUser(NEW_USER);
        existingRecordMetadata.setStatus(RecordState.active);
        existingRecordMetadata.setAcl(this.acl);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("kind/path/123"));

        Map<String, RecordMetadata> existingRecords = new HashMap<>();
        existingRecords.put(RECORD_ID1, existingRecordMetadata);

        Record recordInStorage = new Record();
        recordInStorage.setVersion(123456L);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1))).thenReturn(existingRecords);

        String recordFromStorage = new Gson().toJson(recordInStorage);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(true);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata);
        when(this.authService.hasValidAccess(any(), any())).thenReturn(recordMetadataList);

        when(this.cloudStorage.read(existingRecordMetadata, 123456L, false)).thenReturn(recordFromStorage);

        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER);
        assertEquals(USER, transferInfo.getUser());
        assertEquals(new Integer(1), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(1)).persistRecordBatch(any());
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());
    }


    @Test
    public void should_throwAppException400_whenAclDoesNotMatchTenant() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(false);
        this.record1.setId(null);
        this.record2.setId(null);
        this.acl.setViewers(INVALID_ACL);
        this.acl.setOwners(INVALID_ACL);

        try {
            this.sut.createUpdateRecords(false, this.records, USER);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid ACL", e.getError().getReason());
            assertEquals(
                    "Acl not match with tenant or domain",
                    e.getError().getMessage());
        } catch (Exception e) {
            fail("should not throw any other exception");
        }
    }

    @Test
    public void should_allowUpdateRecord_when_originalRecordWasSoftDeleted() {

        this.records.remove(this.record2);

        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);
        this.record1.setAcl(this.acl);

        when(this.authService.isValidAcl(this.headers,
                Sets.newHashSet("data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com"))).thenReturn(true);

        RecordMetadata existingRecordMetadata = new RecordMetadata();
        existingRecordMetadata.setId(RECORD_ID1);
        existingRecordMetadata.setKind(KIND_1);
        existingRecordMetadata.setStatus(RecordState.deleted);
        existingRecordMetadata.setAcl(this.acl);
        existingRecordMetadata.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata);

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1))).thenReturn(output);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(true);

        this.sut.createUpdateRecords(false, this.records, USER);

        ArgumentCaptor<TransferBatch> captor = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService).persistRecordBatch(captor.capture());

        TransferBatch batch = captor.getValue();
        assertEquals(new Integer(1), batch.getTransferInfo().getRecordCount());
        assertEquals(RecordState.active, batch.getRecords().get(0).getRecordMetadata().getStatus());
    }
}