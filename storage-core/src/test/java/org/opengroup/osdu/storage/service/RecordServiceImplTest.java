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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;

import com.google.common.collect.Lists;

import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.storage.PersistenceHelper;

@RunWith(MockitoJUnitRunner.class)
public class RecordServiceImplTest {

    private static final String RECORD_ID = "tenant1:record:anyId";
    private static final String TENANT_NAME = "TENANT1";

    @Mock
    private IRecordsMetadataRepository recordRepository;

    @Mock
    private ICloudStorage cloudStorage;

    @Mock
    private IMessageBus pubSubClient;

    @Mock
    private IEntitlementsAndCacheService entitlementsAndCacheService;

    @Mock
    private IPersistenceService persistenceService;

    @Mock
    private DpsHeaders headers;

    @Mock
    private TenantInfo tenant;

    @Mock
    private ITenantFactory tenantFactory;

    @InjectMocks
    private RecordServiceImpl sut;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private DataAuthorizationService dataAuthorizationService;

    @Before
    public void setup() {
        mock(PersistenceHelper.class);

        when(this.tenant.getName()).thenReturn(TENANT_NAME);
        when(this.headers.getPartitionIdWithFallbackToAccountId()).thenReturn(TENANT_NAME);
        when(this.tenantFactory.exists(TENANT_NAME)).thenReturn(true);
        when(this.tenantFactory.getTenantInfo(TENANT_NAME)).thenReturn(this.tenant);
    }

    @Test
    public void should_throwHttp404_when_purgingRecordWhichDoesNotExist() {
        try {
            this.sut.purgeRecord(RECORD_ID);

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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);
        when(this.entitlementsAndCacheService.hasOwnerAccess(any(), any())).thenReturn(true);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        this.sut.purgeRecord(RECORD_ID);
        verify(this.auditLogger).purgeRecordSuccess(any());

        verify(this.recordRepository).delete(RECORD_ID);

        verify(this.cloudStorage).delete(record);

        PubSubInfo pubsubMsg = new PubSubInfo(RECORD_ID, "any kind", OperationType.delete);

        verify(this.pubSubClient).publishMessage(this.headers, pubsubMsg);
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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);

        when(this.entitlementsAndCacheService.hasOwnerAccess(any(), any())).thenReturn(false);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(false);

        try {
            this.sut.purgeRecord(RECORD_ID);

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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);
        when(this.entitlementsAndCacheService.hasOwnerAccess(any(), any())).thenReturn(true);

        doThrow(originalException).when(this.recordRepository).delete(RECORD_ID);

        try {
            this.sut.purgeRecord(RECORD_ID);

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
            this.sut.purgeRecord("invalidID");

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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);
        when(this.entitlementsAndCacheService.hasOwnerAccess(any(), any())).thenReturn(true);
        when(this.dataAuthorizationService.validateOwnerAccess(any(), any())).thenReturn(true);

        doThrow(new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
                "The user is not authorized to perform this action")).when(this.cloudStorage).delete(record);
        try {
            this.sut.purgeRecord(RECORD_ID);

            fail("Should not succeed");
        } catch (AppException e) {
            verify(this.recordRepository).createOrUpdate(Lists.newArrayList(record));
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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);
        when(this.dataAuthorizationService.hasAccess(any(), any())).thenReturn(true);

        when(this.cloudStorage.hasAccess(record)).thenReturn(true);

        this.sut.deleteRecord(RECORD_ID, "anyUserName");
        verify(this.auditLogger).deleteRecordSuccess(any());

        ArgumentCaptor<List> recordListCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.recordRepository).createOrUpdate(recordListCaptor.capture());

        List capturedRecords = recordListCaptor.getValue();
        assertEquals(1, capturedRecords.size());

        RecordMetadata capturedRecord = (RecordMetadata) capturedRecords.get(0);
        assertEquals("any kind", capturedRecord.getKind());
        assertEquals(RECORD_ID, capturedRecord.getId());
        assertEquals(RecordState.deleted, capturedRecord.getStatus());
        assertNotNull(capturedRecord.getModifyTime());
        assertEquals("anyUserName", capturedRecord.getModifyUser());

        ArgumentCaptor<PubSubInfo> pubsubMessageCaptor = ArgumentCaptor.forClass(PubSubInfo.class);

        verify(this.pubSubClient).publishMessage(eq(this.headers), pubsubMessageCaptor.capture());

        PubSubInfo capturedMessage = pubsubMessageCaptor.getValue();
        assertEquals(RECORD_ID, capturedMessage.getId());
        assertEquals("any kind", capturedMessage.getKind());
        assertEquals(OperationType.delete, capturedMessage.getOp());
    }

    @Test
    public void should_returnForbidden_when_tryingToDeleteRecordWhichUserDoesNotHaveAccessTo() {
        RecordMetadata record = new RecordMetadata();
        record.setKind("any kind");
        record.setId(RECORD_ID);
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);

        when(this.cloudStorage.hasAccess(record)).thenReturn(false);
        when(this.dataAuthorizationService.hasAccess(any(), any())).thenReturn(false);

        try {
            this.sut.deleteRecord(RECORD_ID, "anyUser");

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

        when(this.recordRepository.get(RECORD_ID)).thenReturn(record);

        try {
            this.sut.deleteRecord(RECORD_ID, "anyUserName");

            fail("Should not succeed!");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_NOT_FOUND, e.getError().getCode());
            assertEquals("Record not found", e.getError().getReason());
            assertEquals("Record with id '" + RECORD_ID + "' does not exist", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }
}