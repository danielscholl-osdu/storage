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

package org.opengroup.osdu.storage.api;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.mapper.CreateUpdateRecordsResponseMapper;
import org.opengroup.osdu.storage.response.CreateUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.IngestionService;
import org.opengroup.osdu.storage.service.QueryService;
import org.opengroup.osdu.storage.service.RecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
public class RecordApiTest {

    private final String USER = "user";
    private final String TENANT = "tenant1";
    private final String RECORD_ID = "osdu:anyID:any";
    private final String COLLABORATION_DIRECTIVES = "id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=TestApp";
    private final Integer LIMIT = 2;
    private final long FROM_VERSION = 0;

    private final String DEFAULT_VERSION_IDS =  null;
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    @Mock
    private IngestionService ingestionService;

    @Mock
    private QueryService queryService;

    @Mock
    private RecordService recordService;

    @Mock
    private DpsHeaders httpHeaders;

    @Mock
    private CollaborationContextFactory collaborationContextFactory;

    @Mock
    private CreateUpdateRecordsResponseMapper createUpdateRecordsResponseMapper;

    @InjectMocks
    private RecordApi sut;

    @BeforeEach
    public void setup() {
        initMocks(this);

        lenient().when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);
        lenient().when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());
        TenantInfo tenant = new TenantInfo();
        tenant.setName(this.TENANT);
    }

    @Test
    public void should_returnsHttp201_when_creatingOrUpdatingRecordsSuccessfully() {
        TransferInfo transfer = new TransferInfo();
        transfer.setSkippedRecords(singletonList("ID1"));
        transfer.setVersion(System.currentTimeMillis() * 1000L + (new Random()).nextInt(1000) + 1);

        Record r1 = new Record();
        r1.setId("ID1");

        Record r2 = new Record();
        r2.setId("ID2");

        List<Record> records = new ArrayList<>();
        records.add(r1);
        records.add(r2);

        when(this.ingestionService.createUpdateRecords(false, records, this.USER, Optional.empty())).thenReturn(transfer); // check 
        when(createUpdateRecordsResponseMapper.map(transfer, records)).thenReturn(new CreateUpdateRecordsResponse());

        CreateUpdateRecordsResponse response = this.sut.createOrUpdateRecords(COLLABORATION_DIRECTIVES, false, records);
        assertNotNull(response);
    }

    @Test
    public void should_returnsHttp201_when_creatingOrUpdatingRecordsSuccessfullyWithCollaborationContext() {
        TransferInfo transfer = new TransferInfo();
        transfer.setSkippedRecords(singletonList("ID1"));
        transfer.setVersion(System.currentTimeMillis() * 1000L + (new Random()).nextInt(1000) + 1);

        Record r1 = new Record();
        r1.setId("ID1");

        Record r2 = new Record();
        r2.setId("ID2");

        List<Record> records = new ArrayList<>();
        records.add(r1);
        records.add(r2);

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        when(this.ingestionService.createUpdateRecords(false, records, this.USER, COLLABORATION_CONTEXT)).thenReturn(transfer); // check
        when(createUpdateRecordsResponseMapper.map(transfer, records)).thenReturn(new CreateUpdateRecordsResponse());

        CreateUpdateRecordsResponse response = this.sut.createOrUpdateRecords(COLLABORATION_DIRECTIVES, false, records);
        assertNotNull(response);
    }

    @Test
    public void should_returnRecordIds_when_recordsAreNotUpdatedBecauseOfSkipDupes() {
        TransferInfo transfer = new TransferInfo();
        transfer.getSkippedRecords().add("id5");

        Record r1 = new Record();
        r1.setId("ID1");

        List<Record> records = new ArrayList<>();
        records.add(r1);

        when(this.ingestionService.createUpdateRecords(false, records, this.USER, Optional.empty())).thenReturn(transfer);
        when(createUpdateRecordsResponseMapper.map(transfer, records)).thenReturn(new CreateUpdateRecordsResponse());

        CreateUpdateRecordsResponse response = this.sut.createOrUpdateRecords(COLLABORATION_DIRECTIVES, false, records);
        assertNotNull(response);
    }

    @Test
    public void should_returnHttp200_when_gettingRecordVersionsSuccessfully() {
        List<Long> versions = new ArrayList<Long>();
        versions.add(1L);
        versions.add(2L);

        RecordVersions recordVersions = new RecordVersions();
        recordVersions.setRecordId(RECORD_ID);
        recordVersions.setVersions(versions);

        when(this.queryService.listVersions(RECORD_ID, Optional.empty())).thenReturn(recordVersions);

        ResponseEntity response = this.sut.getRecordVersions(COLLABORATION_DIRECTIVES, RECORD_ID);

        RecordVersions versionsResponse = (RecordVersions) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(RECORD_ID, versionsResponse.getRecordId());
        assertTrue(versionsResponse.getVersions().contains(1L));
        assertTrue(versionsResponse.getVersions().contains(2L));
    }

    @Test
    public void should_returnHttp200_when_gettingRecordVersionsSuccessfullyWithCollaborationContext() {
        List<Long> versions = new ArrayList<Long>();
        versions.add(1L);
        versions.add(2L);

        RecordVersions recordVersions = new RecordVersions();
        recordVersions.setRecordId(RECORD_ID);
        recordVersions.setVersions(versions);

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        when(this.queryService.listVersions(RECORD_ID, COLLABORATION_CONTEXT)).thenReturn(recordVersions);

        ResponseEntity response = this.sut.getRecordVersions(COLLABORATION_DIRECTIVES, RECORD_ID);

        RecordVersions versionsResponse = (RecordVersions) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(RECORD_ID, versionsResponse.getRecordId());
        assertTrue(versionsResponse.getVersions().contains(1L));
        assertTrue(versionsResponse.getVersions().contains(2L));
    }

    @Test
    public void should_returnHttp204_when_purgingRecordSuccessfully() {
        ResponseEntity response = this.sut.purgeRecord(COLLABORATION_DIRECTIVES, RECORD_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_purgingRecordSuccessfullyWithCollaborationContext() {
        doNothing().when(recordService).purgeRecord(RECORD_ID, COLLABORATION_CONTEXT);
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        ResponseEntity response = this.sut.purgeRecord(COLLABORATION_DIRECTIVES, RECORD_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_purgingRecordVersions_byLimit_successfully() {
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());
        doNothing().when(recordService).purgeRecordVersions(RECORD_ID, DEFAULT_VERSION_IDS, LIMIT, FROM_VERSION, USER, Optional.empty());
        ResponseEntity response = this.sut.purgeRecordVersions(COLLABORATION_DIRECTIVES, RECORD_ID, DEFAULT_VERSION_IDS, LIMIT, FROM_VERSION);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_purgingRecordVersions_byLimit_successfullyWithCollaborationContext() {
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        doNothing().when(recordService).purgeRecordVersions(RECORD_ID, DEFAULT_VERSION_IDS, LIMIT, FROM_VERSION, USER, COLLABORATION_CONTEXT);
        ResponseEntity response = this.sut.purgeRecordVersions(COLLABORATION_DIRECTIVES, RECORD_ID, DEFAULT_VERSION_IDS, LIMIT, FROM_VERSION);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_deletingRecordSuccessfullyWithCollaborationContext() {
        doNothing().when(recordService).deleteRecord(RECORD_ID, USER,COLLABORATION_CONTEXT);
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        ResponseEntity response = this.sut.deleteRecord(COLLABORATION_DIRECTIVES, RECORD_ID);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp200_when_gettingTheLatestVersionOfARecordSuccessfully() {
        when(this.queryService.getRecordInfo(RECORD_ID, new String[] {}, Optional.empty())).thenReturn(RECORD_ID);

        ResponseEntity response = this.sut.getLatestRecordVersion(COLLABORATION_DIRECTIVES, RECORD_ID, new String[] {});

        String recordInfoResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordInfoResponse.contains(RECORD_ID));
    }

    @Test
    public void should_returnHttp200_when_gettingTheLatestVersionOfARecordSuccessfullyWithCollaborationContext() {
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        when(this.queryService.getRecordInfo(RECORD_ID, new String[] {}, COLLABORATION_CONTEXT)).thenReturn(RECORD_ID);

        ResponseEntity response = this.sut.getLatestRecordVersion(COLLABORATION_DIRECTIVES, RECORD_ID, new String[] {});

        String recordInfoResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordInfoResponse.contains(RECORD_ID));
    }

    @Test
    public void should_returnHttp200_when_gettingSpecificVersionOfARecordSuccessfully() {
        final long VERSION = 1L;

        String expectedRecord = "{\"id\": \"osdu:anyID:any\",\r\n\"version\": 1}";

        when(this.queryService.getRecordInfo(RECORD_ID, VERSION, new String[] {}, Optional.empty())).thenReturn(expectedRecord);

        ResponseEntity response = this.sut.getSpecificRecordVersion(COLLABORATION_DIRECTIVES, RECORD_ID, VERSION, new String[] {});

        String recordResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordResponse.contains(RECORD_ID));
        assertTrue(recordResponse.contains(Long.toString(VERSION)));
    }

    @Test
    public void should_returnHttp200_when_gettingSpecificVersionOfARecordSuccessfullyWithCollaborationContext() {
        final long VERSION = 1L;

        String expectedRecord = "{\"id\": \"osdu:anyID:any\",\r\n\"version\": 1}";

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        when(this.queryService.getRecordInfo(RECORD_ID, VERSION, new String[] {}, COLLABORATION_CONTEXT)).thenReturn(expectedRecord);

        ResponseEntity response = this.sut.getSpecificRecordVersion(COLLABORATION_DIRECTIVES, RECORD_ID, VERSION, new String[] {});

        String recordResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordResponse.contains(RECORD_ID));
        assertTrue(recordResponse.contains(Long.toString(VERSION)));
    }

    @Test
    public void should_returnHttp204_when_bulkSoftDeletingRecordsSuccessfullyWithCollaborationContext() {
        String id2 = "osdu:anyID2:any";
        List<String> recordIds = new ArrayList<>();
        recordIds.add(RECORD_ID);
        recordIds.add(id2);
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);

        ResponseEntity response = this.sut.bulkDeleteRecords(COLLABORATION_DIRECTIVES, recordIds);
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_deletingRecordSuccessfully() {
        ResponseEntity response = this.sut.deleteRecord(COLLABORATION_DIRECTIVES, RECORD_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp204_when_bulkDeleteRecordsSuccessfully() {
        ResponseEntity response = this.sut.bulkDeleteRecords(COLLABORATION_DIRECTIVES, singletonList(RECORD_ID));

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_allowAccessToCreateOrUpdateRecords_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("createOrUpdateRecords",  String.class ,boolean.class, List.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetRecordVersions_when_userBelongsToViewerCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getRecordVersions", String.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToPurgeRecord_when_userBelongsToAdminGroup() throws Exception {

        Method method = this.sut.getClass().getMethod("purgeRecord", String.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertFalse(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToDeleteRecord_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("deleteRecord", String.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToBulkDeleteRecords_when_userBelongsToCreatorOrAdminGroups() throws Exception {
        Method method = this.sut.getClass().getMethod("deleteRecord", String.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetLatestVersionOfRecord_when_userBelongsToViewerCreatorOrAdminGroups()
            throws Exception {

        Method method = this.sut.getClass().getMethod("getLatestRecordVersion", String.class, String.class, String[].class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetSpecificRecordVersion_when_userBelongsToViewerCreatorOrAdminGroups()
            throws Exception {

        Method method = this.sut.getClass().getMethod("getSpecificRecordVersion", String.class, String.class,
                long.class, String[].class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }
}
