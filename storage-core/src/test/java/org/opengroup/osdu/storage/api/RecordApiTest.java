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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.storage.IngestionService;
import org.opengroup.osdu.storage.mapper.CreateUpdateRecordsResponseMapper;
import org.opengroup.osdu.storage.response.CreateUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.QueryService;
import org.opengroup.osdu.storage.service.RecordService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@RunWith(MockitoJUnitRunner.class)
public class RecordApiTest {

    private final String USER = "user";
    private final String TENANT = "tenant1";
    private final String RECORD_ID = "osdu:anyID:any";

    @Mock
    private IngestionService ingestionService;

    @Mock
    private QueryService queryService;

    @Mock
    private RecordService recordService;

    @Mock
    private DpsHeaders httpHeaders;

    @Mock
    private CreateUpdateRecordsResponseMapper createUpdateRecordsResponseMapper;

    @InjectMocks
    private RecordApi sut;

    @Before
    public void setup() {
        initMocks(this);

        when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);
        when(this.httpHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(this.TENANT);
        TenantInfo tenant = new TenantInfo();
        tenant.setName(this.TENANT);
    }

    @Test
    public void should_returnsHttp201_when_creatingOrUpdatingRecordsSuccessfully() {
        TransferInfo transfer = new TransferInfo();
        transfer.setSkippedRecords(Collections.singletonList("ID1"));
        transfer.setVersion(System.currentTimeMillis() * 1000L + (new Random()).nextInt(1000) + 1);

        Record r1 = new Record();
        r1.setId("ID1");

        Record r2 = new Record();
        r2.setId("ID2");

        List<Record> records = new ArrayList<>();
        records.add(r1);
        records.add(r2);

        when(this.ingestionService.createUpdateRecords(false, records, this.USER)).thenReturn(transfer);
        when(createUpdateRecordsResponseMapper.map(transfer, records)).thenReturn(new CreateUpdateRecordsResponse());

        CreateUpdateRecordsResponse response = this.sut.createOrUpdateRecords(false, records);
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

        when(this.ingestionService.createUpdateRecords(false, records, this.USER)).thenReturn(transfer);
        when(createUpdateRecordsResponseMapper.map(transfer, records)).thenReturn(new CreateUpdateRecordsResponse());

        CreateUpdateRecordsResponse response = this.sut.createOrUpdateRecords(false, records);
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

        when(this.queryService.listVersions(RECORD_ID)).thenReturn(recordVersions);

        ResponseEntity response = this.sut.getRecordVersions(RECORD_ID);

        RecordVersions versionsResponse = (RecordVersions) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(RECORD_ID, versionsResponse.getRecordId());
        assertTrue(versionsResponse.getVersions().contains(1L));
        assertTrue(versionsResponse.getVersions().contains(2L));
    }

    @Test
    public void should_returnHttp204_when_purgingRecordSuccessfully() {
        ResponseEntity response = this.sut.purgeRecord(RECORD_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp200_when_gettingTheLatestVersionOfARecordSuccessfully() {
        when(this.queryService.getRecordInfo(RECORD_ID, new String[] {})).thenReturn(RECORD_ID);

        ResponseEntity response = this.sut.getLatestRecordVersion(RECORD_ID, new String[] {});

        String recordInfoResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordInfoResponse.contains(RECORD_ID));
    }

    @Test
    public void should_returnHttp200_when_gettingSpecificVersionOfARecordSuccessfully() {
        final long VERSION = 1L;

        String expectedRecord = "{\"id\": \"osdu:anyID:any\",\r\n\"version\": 1}";

        when(this.queryService.getRecordInfo(RECORD_ID, VERSION, new String[] {})).thenReturn(expectedRecord);

        ResponseEntity response = this.sut.getSpecificRecordVersion(RECORD_ID, VERSION, new String[] {});

        String recordResponse = response.getBody().toString();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertTrue(recordResponse.contains(RECORD_ID));
        assertTrue(recordResponse.contains(Long.toString(VERSION)));
    }

    @Test
    public void should_returnHttp204_when_deletingRecordSuccessfully() {
        ResponseEntity response = this.sut.deleteRecord(RECORD_ID);

        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatusCodeValue());
    }

    @Test
    public void should_allowAccessToCreateOrUpdateRecords_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("createOrUpdateRecords", boolean.class, List.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetRecordVersions_when_userBelongsToViewerCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getRecordVersions", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToPurgeRecord_when_userBelongsToAdminGroup() throws Exception {

        Method method = this.sut.getClass().getMethod("purgeRecord", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertFalse(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToDeleteRecord_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("deleteRecord", String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetLatestVersionOfRecord_when_userBelongsToViewerCreatorOrAdminGroups()
            throws Exception {

        Method method = this.sut.getClass().getMethod("getLatestRecordVersion", String.class, String[].class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetSpecificRecordVersion_when_userBelongsToViewerCreatorOrAdminGroups()
            throws Exception {

        Method method = this.sut.getClass().getMethod("getSpecificRecordVersion", String.class, long.class,
                String[].class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }
}
