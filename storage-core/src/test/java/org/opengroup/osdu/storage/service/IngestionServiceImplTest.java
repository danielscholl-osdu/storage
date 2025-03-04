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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.CrcHashGenerator;
import org.opengroup.osdu.storage.util.RecordBlocks;
import org.opengroup.osdu.storage.util.RecordConstants;
import org.opengroup.osdu.storage.util.RecordTestUtil;
import org.opengroup.osdu.storage.util.api.RecordUtil;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

@ExtendWith(MockitoExtension.class)
public class IngestionServiceImplTest {

    @Mock
    private IRecordsMetadataRepository recordRepository;

    RecordBlocks recordBlocks;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private PersistenceService persistenceService;

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
    private IEntitlementsExtensionService authService;

    @Mock
    private IEntitlementsFactory entitlementsFactory;

    @Mock
    private IEntitlementsService entitlementsService;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private RecordUtil recordUtil;

    @Mock
    private IOPAService opaService;

    @Mock
    private IFeatureFlag featureFlag;

    @Spy
    CrcHashGenerator crcHashGenerator;

    @InjectMocks
    private IngestionServiceImpl sut;

    private static final String RECORD_ID1 = "tenant1:kind:record1";
    private static final String RECORD_ID2 = "tenant1:crazy:record2";
    private static final String KIND_1 = "tenant1:test:kind:1.0.0";
    private static final String KIND_2 = "tenant1:test:crazy:2.0.2";
    private static final String USER = "testuser@gmail.com";
    private static final String NEW_USER = "newuser@gmail.com";
    private static final String TENANT = "tenant1";
    private static final UUID COLLABORATION_ID =UUID.randomUUID();
    private static final String[] VALID_ACL = new String[] { "data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com" };
    private static final String[] INVALID_ACL = new String[] { "data.email1@test.test.com", "data.test@test.test.com" };
    private static final String[] NON_OWNER_ACL = new String[] { "data.not_owner@test.test.com" };

    private Record record1;
    private Record record2;
    private Record record1inCollaboration;
    private Record record2inCollaboration;

    private List<Record> records;
    private Acl acl;

    @BeforeEach
    public void setup() throws PartitionException, EntitlementsException {

        List<String> userHeaders = new ArrayList<>();
        userHeaders.add(USER);

        this.acl = new Acl();

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        Groups groups = new Groups();
        List<GroupInfo> groupsInfo = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setEmail("test.group@mydomain.com");
        groupsInfo.add(groupInfo);
        groups.setGroups(groupsInfo);

        this.record1 = new Record();
        this.record1.setKind(KIND_1);
        this.record1.setId(RECORD_ID1);
        this.record1.setLegal(legal);
        // set up empty ancestry for record1
        RecordAncestry  ancestry = new RecordAncestry();
        this.record1.setAncestry(ancestry);

        this.record2 = new Record();
        this.record2.setKind(KIND_2);
        this.record2.setId(RECORD_ID2);
        this.record2.setLegal(legal);

        this.record1.setAcl(this.acl);
        this.record2.setAcl(this.acl);

        this.record1inCollaboration = RecordTestUtil.ofOtherAndCollaboration(record1, COLLABORATION_ID);
        this.record2inCollaboration = RecordTestUtil.ofOtherAndCollaboration(record2, COLLABORATION_ID);


        this.records = new ArrayList<>();
        this.records.add(this.record1);
        this.records.add(this.record2);

        lenient().when(this.tenant.getName()).thenReturn(TENANT);
        lenient().when(this.authService.hasOwnerAccess(any(),any())).thenReturn(true);
        recordBlocks = new RecordBlocks(cloudStorage, crcHashGenerator);
        sut.recordBlocks = recordBlocks;

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

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Bad request", exception.getError().getReason());
        assertEquals("Cannot update the same record multiple times in the same request. Id: tenant1:record:123",
                exception.getError().getMessage());
    }

    @Test
    public void should_throwAppException400_when_recordIdDoesNotFollowTenantNameConvention() {

        final String INVALID_RECORD_ID = "gasguys:record:123";

        this.record1.setId(INVALID_RECORD_ID);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Invalid record id", exception.getError().getReason());
        assertEquals(
                "The record 'gasguys:record:123' does not follow the naming convention: The record id must be in the format of <tenantId>:<kindSubType>:<uniqueId>. Example: tenant1:kind:<uuid>",
                exception.getError().getMessage());
    }

    @Test
    public void should_throwAppException400_when_recordIdSizeGreaterThanLimit() {

        String INVALID_RECORD_ID = "tenant1:record:longidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongid" +
                "longidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongid" +
                "longidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongid" +
                "longidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongid" +
                "longidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongidlongid" +
                "longidlongidlongidlongidlongidlongidlongidlongid";

        this.record1.setId(INVALID_RECORD_ID);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Invalid record id", exception.getError().getReason());
        assertEquals("The record '" + INVALID_RECORD_ID + "' does not follow the record id size convention: The record id must be no longer than " +
                RecordConstants.RECORD_ID_MAX_SIZE_IN_BYTES + " bytes", exception.getError().getMessage());
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

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        assertEquals(2, transferInfo.getRecordCount());

        ArgumentCaptor<List> ids = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository).get(ids.capture(), eq(Optional.empty()));

        List<String> capturedIds = ids.getValue();
        assertEquals(2, capturedIds.size());
        assertTrue(capturedIds.get(0).startsWith("tenant1:"));
        assertTrue(capturedIds.get(1).startsWith("tenant1:"));

        ArgumentCaptor<TransferBatch> transferCaptor = ArgumentCaptor.forClass(TransferBatch.class);
        verify(this.persistenceService).persistRecordBatch(transferCaptor.capture(), eq(Optional.empty()));
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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void should_createTwoRecordsInCollaboration_when_twoRecordsWithoutIdArePersisted() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.record1.setId(null);
        this.record2.setId(null);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        when(this.cloudStorage.hasAccess(new RecordMetadata[] {})).thenReturn(true);
        final CollaborationContext collaborationContext = new CollaborationContext(COLLABORATION_ID, "app1", Collections.emptyMap());
        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER, Optional.of(collaborationContext));
        assertEquals(Integer.valueOf(2), transferInfo.getRecordCount());

        ArgumentCaptor<List> ids = ArgumentCaptor.forClass(List.class);
        verify(this.recordRepository).get(ids.capture(), eq(Optional.of(collaborationContext)));

        List<String> capturedIds = ids.getValue();
        assertEquals(2, capturedIds.size());
        assertTrue(capturedIds.get(0).startsWith("tenant1:"));
        assertTrue(capturedIds.get(1).startsWith("tenant1:"));

        ArgumentCaptor<TransferBatch> transferCaptor = ArgumentCaptor.forClass(TransferBatch.class);
        verify(this.persistenceService).persistRecordBatch(transferCaptor.capture(), eq(Optional.of(collaborationContext)));
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
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(false);
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

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1, RECORD_ID2), Optional.empty())).thenReturn(output);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(false);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        assertEquals("Access denied", exception.getError().getReason());
        assertEquals("The user is not authorized to perform this action", exception.getError().getMessage());
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
        when(this.authService.hasOwnerAccess(any(), any())).thenReturn(false);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_FORBIDDEN, exception.getError().getCode());
        assertEquals("User Unauthorized", exception.getError().getReason());
        assertEquals("User is not authorized to update records.", exception.getError().getMessage());

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

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);
        when(this.cloudStorage.read(existingRecordMetadata1, 3L, false)).thenReturn(new Gson().toJson(this.record1));
        when(this.cloudStorage.read(existingRecordMetadata2, 5L, false)).thenReturn(new Gson().toJson(this.record2));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(2, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_updateTwoRecordsInCollaboration_when_twoRecordIDsAreAlreadyPresentInDataLake() {
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
        existingRecordMetadata2.setAcl(acl);
        existingRecordMetadata2.setGcsVersionPaths(Lists.newArrayList("path/4", "path/5"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(record1inCollaboration.getId(), existingRecordMetadata1);
        output.put(record2inCollaboration.getId(), existingRecordMetadata2);

        final CollaborationContext collaborationContext = new CollaborationContext(COLLABORATION_ID, "app1", Collections.emptyMap());

        when(this.cloudStorage.hasAccess(output.values().toArray(new RecordMetadata[output.size()]))).thenReturn(true);

        when(this.recordRepository.get(any(List.class), eq(Optional.of(collaborationContext)))).thenReturn(output);
        when(this.cloudStorage.read(existingRecordMetadata1, 3L, false)).thenReturn(new Gson().toJson(this.record1));
        when(this.cloudStorage.read(existingRecordMetadata2, 5L, false)).thenReturn(new Gson().toJson(this.record2));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, records, USER, Optional.of(collaborationContext));
        assertEquals(USER, transferInfo.getUser());
        assertEquals(Integer.valueOf(2), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), eq(Optional.of(collaborationContext)));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_includePriorKind_when_kindUpdated() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record1.setKind(KIND_2);
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

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        when(this.authService.hasOwnerAccess(any(), any())).thenReturn(true);
        when(this.cloudStorage.read(existingRecordMetadata1, 3L, false)).thenReturn(new Gson().toJson(this.record1));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, Collections.singletonList(this.record1), USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(1, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
            assertEquals(KIND_1, rp.getRecordMetadata().getPreviousVersionKind());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_disregardUpdateRecord_when_skipDupesAndSameRecordContentUsingDataHashField() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);
        this.records.remove(1);

        Map<String, Object> data = new HashMap<>();
        data.put("country", "USA");
        data.put("state", "TX");

        this.record1.setData(data);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        RecordMetadata updatedRecordMetadata = new RecordMetadata(record1);

        String dataHash = crcHashGenerator.getHash(record1.getData());
        Map<String, String> hashes = new HashMap<>();
        hashes.put("data",dataHash);
        updatedRecordMetadata.setHash(hashes);
        List<String> versions = new ArrayList<>();
        versions.add("kind/id/445");
        updatedRecordMetadata.resetGcsPath(versions);
        updatedRecordMetadata.setStatus(RecordState.active);
        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, updatedRecordMetadata);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);
        when(this.cloudStorage.hasAccess(updatedRecordMetadata)).thenReturn(true);

        Record recordInStorage = new Record();
        recordInStorage.setVersion(3L);
        recordInStorage.setData(data);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);

        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(1, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(0)).persistRecordBatch(any(), eq(Optional.empty()));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void should_disregardUpdateRecord_when_skipDupesAndSameRecordContentAndDataHashFieldUnset() {
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
        updatedRecordMetadata.setStatus(RecordState.active);
        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, updatedRecordMetadata);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);
        when(this.cloudStorage.hasAccess(updatedRecordMetadata)).thenReturn(true);

        Record recordInStorage = new Record();
        recordInStorage.setVersion(3L);
        recordInStorage.setData(data);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);


        when(this.cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(recordInStorage));

        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(1, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(0)).persistRecordBatch(any(), eq(Optional.empty()));
    }

    @Test
    public void should_considerUpdateRecord_when_skipDupesAndDifferentRecordContentUsingDataHash() {
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
        Map<String, String> hashes = new HashMap<>();
        hashes.put("data","some random hash for mismatch");
        hashes.put("meta","some random hash for mismatch2");
        existingRecordMetadata.setHash(hashes);
        Map<String, RecordMetadata> existingRecords = new HashMap<>();
        existingRecords.put(RECORD_ID1, existingRecordMetadata);

        Record recordInStorage = new Record();
        recordInStorage.setVersion(123456L);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1), Optional.empty())).thenReturn(existingRecords);
        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(true);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata);


        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(1, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(1)).persistRecordBatch(any(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());
    }


    @Test
    public void should_considerUpdateRecord_when_skipDupesAndDifferentRecordContentAndDataHashFieldUnset() {
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

        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1), Optional.empty())).thenReturn(existingRecords);

        String recordFromStorage = new Gson().toJson(recordInStorage);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(true);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata);

        when(this.cloudStorage.read(existingRecordMetadata, 123L, false)).thenReturn(recordFromStorage);

        TransferInfo transferInfo = this.sut.createUpdateRecords(true, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(1, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());
        verify(this.persistenceService, times(1)).persistRecordBatch(any(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());
    }


    @Test
    public void should_throwAppException400_whenAclDoesNotMatchTenant() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(false);
        this.record1.setId(null);
        this.record2.setId(null);
        this.acl.setViewers(INVALID_ACL);
        this.acl.setOwners(INVALID_ACL);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });

        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("Invalid ACL", exception.getError().getReason());
        assertEquals(
                "Acl not match with tenant or domain",
                exception.getError().getMessage());

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
        Record recordInStorage = new Record();
        recordInStorage.setVersion(123456L);
        recordInStorage.setId(RECORD_ID1);
        recordInStorage.setKind(KIND_1);
        when(this.recordRepository.get(Lists.newArrayList(RECORD_ID1), Optional.empty())).thenReturn(output);

        when(this.cloudStorage.hasAccess(existingRecordMetadata)).thenReturn(true);
        when(this.cloudStorage.read(any(), anyLong(), anyBoolean())).thenReturn(new Gson().toJson(recordInStorage));

        this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());

        ArgumentCaptor<TransferBatch> captor = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService).persistRecordBatch(captor.capture(), eq(Optional.empty()));

        TransferBatch batch = captor.getValue();
        assertEquals(1, batch.getTransferInfo().getRecordCount());
        assertEquals(RecordState.active, batch.getRecords().get(0).getRecordMetadata().getStatus());
    }

    @Test
    public void should_return404_when_updatingARecordWithNonExistingParent() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        // set up non-empty ancestry
        RecordAncestry ancestry = new RecordAncestry();
        Set<String> parentSet = new HashSet<String>();
        parentSet.add("test:doc:non-existing record id:111");
        ancestry.setParents(parentSet);
        this.record1.setAncestry(ancestry);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getError().getCode());
        assertEquals("Record not found", exception.getError().getReason());
    }

    @Test
    public void should_return404_when_updatingARecordWithNonExistingRecordMetaData() {
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.acl.setViewers(VALID_ACL);
        this.acl.setOwners(VALID_ACL);

        // set up non-empty ancestry
        RecordAncestry ancestry = new RecordAncestry();
        Set<String> parentSet = new HashSet<String>();
        parentSet.add(RECORD_ID1 + ":111");
        ancestry.setParents(parentSet);
        this.record1.setAncestry(ancestry);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_NOT_FOUND, exception.getError().getCode());
        assertEquals("RecordMetadata version not found", exception.getError().getReason());
    }

    @Test
    public void should_return401_when_updatingARecordThatFailDataAuthorizationCheck_IntegrateOPA() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
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

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("User is not authorized to create or update records.").reason("User Unauthorized").code("401").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        AppException exception = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_UNAUTHORIZED, exception.getError().getCode());
        assertEquals("User Unauthorized", exception.getError().getReason());
        assertEquals("User is not authorized to create or update records.", exception.getError().getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void should_updateTwoRecords_when_twoRecordIDsAreAlreadyPresentInDataLake_integrateOPA() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
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
        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(Collections.EMPTY_LIST).build();
        ValidationOutputRecord validationOutputRecord2 = ValidationOutputRecord.builder().id(RECORD_ID2).errors(Collections.EMPTY_LIST).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        validationOutputRecords.add(validationOutputRecord2);
        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);
        when(this.cloudStorage.read(existingRecordMetadata1, 3L, false)).thenReturn(new Gson().toJson(this.record1));
        when(this.cloudStorage.read(existingRecordMetadata2, 5L, false)).thenReturn(new Gson().toJson(this.record2));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, this.records, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(2, transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }
    
    @Test
    public void should_return403_when_updatingExistingRecordThatFailDataAuthorizationCheck_IntegrateOPA() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record1.setAcl(new Acl(VALID_ACL, VALID_ACL));
        
        List<Record> processingRecords = new ArrayList<Record>();
        processingRecords.add(this.record1);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(new Acl(VALID_ACL, NON_OWNER_ACL));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);

        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        AppException e = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, processingRecords, USER, Optional.empty());
        });
        assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
        assertEquals("Access denied", e.getError().getReason());
        assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
    }
    
    @Test
    public void should_return403_when_updatingWithNewRecordThatFailDataAuthorizationCheck_IntegrateOPA() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record1.setAcl(new Acl(VALID_ACL, NON_OWNER_ACL));
        
        List<Record> processingRecords = new ArrayList<Record>();
        processingRecords.add(this.record1);
        
        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(new Acl(VALID_ACL, VALID_ACL));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);

        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        AppException e = assertThrows(AppException.class, ()->{
            this.sut.createUpdateRecords(false, processingRecords, USER, Optional.empty());
        });

        assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
        assertEquals("Access denied", e.getError().getReason());
        assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
    }
    
    @Test
    public void should_success_when_updatingRecordThatPassDataAuthorizationCheck_IntegrateOPA() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record1.setAcl(new Acl(VALID_ACL, VALID_ACL));
        
        List<Record> processingRecords = new ArrayList<Record>();
        processingRecords.add(this.record1);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(new Acl(VALID_ACL, VALID_ACL));
        existingRecordMetadata1.setGcsVersionPaths(Lists.newArrayList("path/1", "path/2", "path/3"));

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), eq(Optional.empty()))).thenReturn(output);

        when(this.cloudStorage.read(existingRecordMetadata1, 3L, false)).thenReturn(new Gson().toJson(this.record1));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, processingRecords, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(Integer.valueOf(1), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), eq(Optional.empty()));
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }

    @Test
    public void createUpdateRecords_shouldNotPerform_gcsArraySizeValidationIfFeatureFlagIsNotSet() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.authService.isValidAcl(any(), any())).thenReturn(true);

        this.record1.setId(RECORD_ID1);
        this.record1.setAcl(new Acl(VALID_ACL, VALID_ACL));

        List<Record> processingRecords = new ArrayList<Record>();
        processingRecords.add(this.record1);

        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(new Acl(VALID_ACL, VALID_ACL));

        List<String> versionsArray = IntStream.rangeClosed(1, 1999).mapToObj((item) -> {
            return "path/" + item;
        }).toList();

        existingRecordMetadata1.setGcsVersionPaths(versionsArray);

        Map<String, RecordMetadata> output = new HashMap<>();
        output.put(RECORD_ID1, existingRecordMetadata1);

        when(this.recordRepository.get(any(List.class), any())).thenReturn(output);

        when(this.cloudStorage.read(existingRecordMetadata1, 1999L, false)).thenReturn(new Gson().toJson(this.record1));

        TransferInfo transferInfo = this.sut.createUpdateRecords(false, processingRecords, USER, Optional.empty());
        assertEquals(USER, transferInfo.getUser());
        assertEquals(Integer.valueOf(1), transferInfo.getRecordCount());
        assertNotNull(transferInfo.getVersion());

        ArgumentCaptor<TransferBatch> transfer = ArgumentCaptor.forClass(TransferBatch.class);

        verify(this.persistenceService, times(1)).persistRecordBatch(transfer.capture(), any());
        verify(this.auditLogger).createOrUpdateRecordsSuccess(any());

        TransferBatch input = transfer.getValue();

        for (RecordProcessing rp : input.getRecords()) {
            assertEquals(OperationType.update, rp.getOperationType());
        }
    }

}
