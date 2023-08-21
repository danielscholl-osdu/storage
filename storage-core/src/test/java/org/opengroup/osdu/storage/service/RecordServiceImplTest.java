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
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.DeletionType;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubDeleteInfo;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.storage.PersistenceHelper;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordChangedV2Delete;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.util.RecordConstants;
import org.opengroup.osdu.storage.util.api.RecordUtil;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class RecordServiceImplTest {

    private static final String RECORD_ID = "tenant1:record:anyId";
    private static final String TENANT_NAME = "TENANT1";

    private static final String RECORD_ID_1 = "tenant1:record1:version";

    private static final String USER_NAME = "testUserName";
    private static final String KIND = "testKind";

    private static final String[] OWNERS = new String[]{"owner1@slb.com", "owner2@slb.com"};
    private static final String[] VIEWERS = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    @Mock
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private IMessageBus pubSubClient;

    @Mock
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Mock
    private DpsHeaders headers;

    @Mock
    private TenantInfo tenant;

    @Mock
    private RecordUtil recordUtil;

    @InjectMocks
    private RecordServiceImpl sut;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private DataAuthorizationService dataAuthorizationService;

    @Mock
    private IFeatureFlag collaborationFeatureFlag;

    @Before
    public void setup() {
        mock(PersistenceHelper.class);

        when(this.tenant.getName()).thenReturn(TENANT_NAME);
    }

    @Test
    public void should_throwHttp404_when_purgingRecordWhichDoesNotExist() {
        try {
            this.sut.purgeRecord(RECORD_ID, Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Record not found", e.getError().getReason());
            assertEquals("Record with id '" + RECORD_ID + "' does not exist", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_purgeRecordSuccessfully_when_recordExistsAndHaveProperPermissions() {

        Acl storageAcl = new Acl();
        String[] viewers = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
        String[] owners = new String[]{"owner1@slb.com", "owner2@slb.com"};
        storageAcl.setViewers(viewers);
        storageAcl.setOwners(owners);

        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setAcl(storageAcl);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        this.sut.purgeRecord(RECORD_ID, Optional.empty());
        verify(this.auditLogger).purgeRecordSuccess(any());

        verify(this.recordRepository).delete(RECORD_ID, Optional.empty());

        verify(this.cloudStorage).delete(record);

        PubSubDeleteInfo pubSubDeleteInfo = new PubSubDeleteInfo(RECORD_ID, "any kind", DeletionType.hard);

        verify(this.pubSubClient).publishMessage(this.headers, pubSubDeleteInfo);
    }


    @Test
    public void should_return403_when_recordExistsButWithoutOwnerPermissions() {
        Acl storageAcl = new Acl();
        String[] viewers = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
        String[] owners = new String[]{"owner1@slb.com", "owner2@slb.com"};
        storageAcl.setViewers(viewers);
        storageAcl.setOwners(owners);

        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setAcl(storageAcl);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);

        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(false);

        try {
            this.sut.purgeRecord(RECORD_ID, Optional.empty());

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(403, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to purge the record", e.getError().getMessage());
        }
    }

    @Test
    public void should_returnThrowOriginalException_when_deletingRecordInDatastoreFails() {
        Acl storageAcl = new Acl();
        String[] viewers = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
        String[] owners = new String[]{"owner1@slb.com", "owner2@slb.com"};
        storageAcl.setViewers(viewers);
        storageAcl.setOwners(owners);

        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setAcl(storageAcl);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        AppException originalException = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "error", "msg");

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        doThrow(originalException).when(this.recordRepository).delete(RECORD_ID, Optional.empty());

        try {
            this.sut.purgeRecord(RECORD_ID, Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            verify(this.auditLogger).purgeRecordFail(any());
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("error", e.getError().getReason());
            assertEquals("msg", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_returnHttp400_when_purgingARecordWhichIdDoesNotMatchTenantName() {
        try {
            this.sut.purgeRecord("invalidID", Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid record ID", e.getError().getReason());
            assertEquals("The record 'invalidID' does not belong to account 'TENANT1'", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }


    @Test
    public void should_rollbackDatastoreRecord_when_deletingRecordInGCSFails() {
        Acl storageAcl = new Acl();
        String[] viewers = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
        String[] owners = new String[]{"owner1@slb.com", "owner2@slb.com"};
        storageAcl.setViewers(viewers);
        storageAcl.setOwners(owners);

        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setAcl(storageAcl);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        doThrow(new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                "The user is not authorized to perform this action")).when(this.cloudStorage).delete(record);
        try {
            this.sut.purgeRecord(RECORD_ID, Optional.empty());

            fail("Should not succeed");
        } catch (AppException e) {
            verify(this.recordRepository).createOrUpdate(Lists.newArrayList(record), Optional.empty());
            verify(this.auditLogger).purgeRecordFail(any());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void should_updateRecordAndPublishMessage_when_deletingRecordSuccessfully() {
        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setId(RECORD_ID);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        this.sut.deleteRecord(RECORD_ID, "anyUserName", Optional.empty());
        verify(this.auditLogger).deleteRecordSuccess(any());

        ArgumentCaptor<List> recordListCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository).createOrUpdate(recordListCaptor.capture(), any());

        List capturedRecords = recordListCaptor.getValue();
        assertEquals(1, capturedRecords.size());

        RecordMetadata capturedRecord = (RecordMetadata) capturedRecords.get(0);
        assertEquals("any kind", capturedRecord.getKind());
        assertEquals(RECORD_ID, capturedRecord.getId());
        assertEquals(RecordState.deleted, capturedRecord.getStatus());
        assertTrue(record.getModifyTime() != 0);
        assertEquals("anyUserName", capturedRecord.getModifyUser());

        ArgumentCaptor<PubSubDeleteInfo> pubsubMessageCaptor = ArgumentCaptor.forClass(PubSubDeleteInfo.class);

        verify(this.pubSubClient).publishMessage(eq(this.headers), pubsubMessageCaptor.capture());

        PubSubDeleteInfo capturedMessage = pubsubMessageCaptor.getValue();
        assertEquals(RECORD_ID, capturedMessage.getId());
        assertEquals("any kind", capturedMessage.getKind());
        assertEquals(OperationType.delete, capturedMessage.getOp());
        assertEquals(DeletionType.soft, capturedMessage.getDeletionType());
    }

    @Test
    public void should_returnForbidden_when_tryingToDeleteRecordWhichUserDoesNotHaveAccessTo() {
        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setId(RECORD_ID);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);

        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(false);


        try {
            this.sut.deleteRecord(RECORD_ID, "anyUser", Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            verify(this.auditLogger).deleteRecordFail(any());
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_returnHttp404_when_deletingRecordAlreadyDeleted() {

        RecordMetadata record = new RecordMetadata();
        record.setStatus(RecordState.deleted);

        when(this.recordRepository.get(RECORD_ID, Optional.empty())).thenReturn(record);

        try {
            this.sut.deleteRecord(RECORD_ID, "anyUserName", Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Record not found", e.getError().getReason());
            assertEquals("Record with id '" + RECORD_ID + "' does not exist", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void shouldDeleteRecords_successfully_when_collaborationFFIsDisabled() {
        RecordMetadata record = buildRecordMetadata();
        Map<String, RecordMetadata> expectedRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID, record);
        }};

        when(collaborationFeatureFlag.isFeatureEnabled(RecordConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(false);
        when(recordRepository.get(singletonList(RECORD_ID), Optional.empty())).thenReturn(expectedRecordMetadataMap);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(true);

        sut.bulkDeleteRecords(singletonList(RECORD_ID), USER_NAME, Optional.empty());

        verify(recordRepository, times(1)).get(singletonList(RECORD_ID), Optional.empty());
        verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
        verify(recordRepository, times(1)).createOrUpdate(singletonList(record), Optional.empty());
        verify(auditLogger, only()).deleteRecordSuccess(singletonList(RECORD_ID));
        verifyPubSubPublished(Optional.empty(), false);

        assertEquals(RecordState.deleted, record.getStatus());
        assertEquals(USER_NAME, record.getModifyUser());
        assertTrue(record.getModifyTime() != 0);
    }

    @Test
    public void shouldBulkDeleteRecords_successfully_when_collaborationFFIsEnabledAndContextIsPresent() {
        RecordMetadata record = buildRecordMetadata();
        Map<String, RecordMetadata> expectedRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(COLLABORATION_CONTEXT.get().getId() + RECORD_ID, record);
        }};

        when(collaborationFeatureFlag.isFeatureEnabled(RecordConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(singletonList(RECORD_ID), COLLABORATION_CONTEXT)).thenReturn(expectedRecordMetadataMap);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(true);

        sut.bulkDeleteRecords(singletonList(RECORD_ID), USER_NAME, COLLABORATION_CONTEXT);

        verify(recordRepository, times(1)).get(singletonList(RECORD_ID), COLLABORATION_CONTEXT);
        verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
        verify(recordRepository, times(1)).createOrUpdate(singletonList(record), COLLABORATION_CONTEXT);
        verify(auditLogger, only()).deleteRecordSuccess(singletonList(RECORD_ID));
        verifyPubSubPublished(COLLABORATION_CONTEXT, true);

        assertEquals(RecordState.deleted, record.getStatus());
        assertEquals(USER_NAME, record.getModifyUser());
        assertNotNull(record.getModifyTime());
        assertTrue(record.getModifyTime() != 0);
    }

    @Test
    public void shouldBulkDeleteRecords_successfully_when_collaborationFFIsEnabledAndContextIsNotPresent() {
        RecordMetadata record = buildRecordMetadata();
        Map<String, RecordMetadata> expectedRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID, record);
        }};

        when(collaborationFeatureFlag.isFeatureEnabled(RecordConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(singletonList(RECORD_ID), Optional.empty())).thenReturn(expectedRecordMetadataMap);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(true);

        sut.bulkDeleteRecords(singletonList(RECORD_ID), USER_NAME, Optional.empty());

        verify(recordRepository, times(1)).get(singletonList(RECORD_ID), Optional.empty());
        verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
        verify(recordRepository, times(1)).createOrUpdate(singletonList(record), Optional.empty());
        verify(auditLogger, only()).deleteRecordSuccess(singletonList(RECORD_ID));
        verifyPubSubPublished(Optional.empty(), true);

        assertEquals(RecordState.deleted, record.getStatus());
        assertEquals(USER_NAME, record.getModifyUser());
        assertTrue(record.getModifyTime() != 0);
    }

    @Test
    public void shouldSoftDeleteRecords_successfully_inCollaborationContext() {
        RecordMetadata record = buildRecordMetadata();
        when(collaborationFeatureFlag.isFeatureEnabled(RecordConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(RECORD_ID, COLLABORATION_CONTEXT)).thenReturn(record);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(true);
        sut.deleteRecord(RECORD_ID, USER_NAME, COLLABORATION_CONTEXT);
        verify(recordRepository, times(1)).get(RECORD_ID, COLLABORATION_CONTEXT);
        verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
        verify(auditLogger, only()).deleteRecordSuccess(singletonList(RECORD_ID));
        verifyPubSubPublished(COLLABORATION_CONTEXT, true);
        assertEquals(RecordState.deleted, record.getStatus());
        assertEquals(USER_NAME, record.getModifyUser());

    }
    @Test
    public void shouldThrowDeleteRecordsException_when_tryingToDeleteRecordsWhichUserDoesNotHaveAccessTo() {
        RecordMetadata record = buildRecordMetadata();
        Map<String, RecordMetadata> expectedRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID, record);
        }};

        when(recordRepository.get(singletonList(RECORD_ID), Optional.empty())).thenReturn(expectedRecordMetadataMap);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(false);

        try {
            sut.bulkDeleteRecords(singletonList(RECORD_ID), USER_NAME, Optional.empty());

            fail("Should not succeed!");
        } catch (DeleteRecordsException e) {
            String errorMsg = String
                    .format("The user is not authorized to perform delete record with id %s", RECORD_ID);
            verify(recordRepository, times(1)).get(singletonList(RECORD_ID), Optional.empty());
            verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
            verify(recordRepository, never()).createOrUpdate(any(), any());
            verify(auditLogger, only()).deleteRecordFail(singletonList(errorMsg));
            verifyNoMoreInteractions(pubSubClient);


            assertEquals(1, e.getNotDeletedRecords().size());
            assertEquals(RECORD_ID, e.getNotDeletedRecords().get(0).getKey());
            assertEquals(errorMsg, e.getNotDeletedRecords().get(0).getValue());

            assertEquals(RecordState.active, record.getStatus());
            assertNull(record.getModifyUser());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void shouldThrowDeleteRecordsException_when_tryingToDeleteRecordsWhenRecordNotFound() {
        RecordMetadata record = buildRecordMetadata();
        Map<String, RecordMetadata> expectedRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID, record);
        }};

        when(recordRepository.get(asList(RECORD_ID, RECORD_ID_1), Optional.empty())).thenReturn(expectedRecordMetadataMap);
        when(dataAuthorizationService.validateOwnerAccess(record, OperationType.delete)).thenReturn(true);

        try {
            sut.bulkDeleteRecords(asList(RECORD_ID, RECORD_ID_1), USER_NAME, Optional.empty());

            fail("Should not succeed!");
        } catch (DeleteRecordsException e) {
            String expectedErrorMessage = "Record with id '" + RECORD_ID_1 + "' not found";
            verify(recordRepository, times(1)).get(asList(RECORD_ID, RECORD_ID_1), Optional.empty());
            verify(dataAuthorizationService, only()).validateOwnerAccess(record, OperationType.delete);
            verify(recordRepository, times(1)).createOrUpdate(singletonList(record), Optional.empty());
            verify(auditLogger, times(1)).deleteRecordSuccess(singletonList(RECORD_ID));
            verify(auditLogger, times(1)).deleteRecordFail(singletonList(expectedErrorMessage));
            verifyPubSubPublished(Optional.empty(), false);

            assertEquals(RecordState.deleted, record.getStatus());
            assertEquals(USER_NAME, record.getModifyUser());
            assertTrue(record.getModifyTime() != 0);
            assertEquals(1, e.getNotDeletedRecords().size());
            assertEquals(RECORD_ID_1, e.getNotDeletedRecords().get(0).getKey());
            assertEquals(expectedErrorMessage, e.getNotDeletedRecords().get(0).getValue());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void shouldThrowAppException_when_tryingToDeleteRecordsForInvalidIds() {
        String errorMsg = String.format("The record '%s' does not follow the naming convention: the first id component must be '%s'",
                RECORD_ID, TENANT_NAME);
        try {
            doThrow(new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record id", errorMsg))
                    .when(recordUtil).validateRecordIds(singletonList(RECORD_ID));

            sut.bulkDeleteRecords(asList(RECORD_ID), USER_NAME, Optional.empty());

            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getError().getCode());
            assertEquals("Invalid record id", e.getError().getReason());
            assertEquals(errorMsg, e.getError().getMessage());

            verifyNoMoreInteractions(recordRepository, entitlementsAndCacheService, auditLogger, pubSubClient);
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    private void verifyPubSubPublished(Optional<CollaborationContext> collaborationContext, boolean isCollaborationFFEnabled) {
        ArgumentCaptor<PubSubDeleteInfo> pubsubMessageCaptor = ArgumentCaptor.forClass(PubSubDeleteInfo.class);
        ArgumentCaptor<RecordChangedV2Delete> recordChangedV2DeleteArgumentCaptor = ArgumentCaptor.forClass(RecordChangedV2Delete.class);

        if(isCollaborationFFEnabled) {
            verify(this.pubSubClient).publishMessage(eq(collaborationContext), eq(this.headers), recordChangedV2DeleteArgumentCaptor.capture());
            RecordChangedV2Delete capturedMessage = recordChangedV2DeleteArgumentCaptor.getValue();
            assertEquals(RECORD_ID, capturedMessage.getId());
            assertEquals(KIND, capturedMessage.getKind());
            assertEquals(OperationType.delete, capturedMessage.getOp());
            assertEquals(DeletionType.soft, capturedMessage.getDeletionType());
            assertEquals(USER_NAME, capturedMessage.getModifiedBy());
        }

        if (!collaborationContext.isPresent()) {
            verify(this.pubSubClient).publishMessage(eq(this.headers), pubsubMessageCaptor.capture());
            PubSubDeleteInfo capturedMessage = pubsubMessageCaptor.getValue();
            assertEquals(RECORD_ID, capturedMessage.getId());
            assertEquals(KIND, capturedMessage.getKind());
            assertEquals(OperationType.delete, capturedMessage.getOp());
            assertEquals(DeletionType.soft, capturedMessage.getDeletionType());
        }
    }

    private RecordMetadata buildRecordMetadata() {
        Acl acl = new Acl();
        acl.setViewers(VIEWERS);
        acl.setOwners(OWNERS);

        RecordMetadata record = new RecordMetadata();
        record.setKind(KIND);
        record.setAcl(acl);
        record.setId(RECORD_ID);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(asList("path/1", "path/2", "path/3"));
        return record;
    }
}
