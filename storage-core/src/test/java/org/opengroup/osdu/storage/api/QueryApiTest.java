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

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.Lists;
import org.mockito.Spy;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.service.BatchService;
import org.opengroup.osdu.storage.util.EncodeDecode;
import org.springframework.http.ResponseEntity;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.access.prepost.PreAuthorize;

@RunWith(MockitoJUnitRunner.class)
public class QueryApiTest {

    @Mock
    private BatchService batchService;

    @Spy
    private EncodeDecode encodeDecode;

    @InjectMocks
    private QueryApi sut;

    @Test
    public void should_returnHttp200_when_gettingRecordsSuccessfully() {
        MultiRecordIds input = new MultiRecordIds();
        input.setRecords(Lists.newArrayList("id1", "id2"));

        MultiRecordInfo output = new MultiRecordInfo();
        List<Record> validRecords = new ArrayList<>();

        Record record1 = new Record();
        record1.setId("id1");

        Record record2 = new Record();
        record2.setId("id2");

        validRecords.add(record1);
        validRecords.add(record2);
        output.setRecords(validRecords);

        when(this.batchService.getMultipleRecords(input)).thenReturn(output);

        ResponseEntity response = this.sut.getRecords(input);

        MultiRecordInfo records = (MultiRecordInfo) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertNull(records.getInvalidRecords());
        assertNull(records.getRetryRecords());
        assertEquals(2, records.getRecords().size());
        assertTrue(records.getRecords().get(0).toString().contains("id1"));
        assertTrue(records.getRecords().get(1).toString().contains("id2"));
    }

    @Test
    public void should_returnHttp200_when_gettingAllKindsSuccessfully() {
        final String CURSOR = "any cursor";
        final String ENCODED_CURSOR = Base64.getEncoder().encodeToString("any cursor".getBytes());
        final int LIMIT = 10;

        List<String> kinds = new ArrayList<String>();
        kinds.add("kind1");
        kinds.add("kind2");
        kinds.add("kind3");

        DatastoreQueryResult allKinds = new DatastoreQueryResult();
        allKinds.setCursor("new cursor");
        allKinds.setResults(kinds);

        when(this.batchService.getAllKinds(CURSOR, LIMIT)).thenReturn(allKinds);

        ResponseEntity response = this.sut.getKinds(ENCODED_CURSOR, LIMIT);

        DatastoreQueryResult allKindsResult = (DatastoreQueryResult) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(3, allKindsResult.getResults().size());
        assertTrue(allKindsResult.getResults().contains("kind1"));
        assertTrue(allKindsResult.getResults().contains("kind2"));
        assertTrue(allKindsResult.getResults().contains("kind3"));
    }

    @Test
    public void should_returnHttp200_when_gettingAllRecordsFromKindSuccessfully() {
        final String CURSOR = "any cursor";
        final String ENCODED_CURSOR = Base64.getEncoder().encodeToString("any cursor".getBytes());

        final String KIND = "any kind";
        final int LIMIT = 10;

        List<String> recordIds = new ArrayList<String>();
        recordIds.add("id1");
        recordIds.add("id2");
        recordIds.add("id3");

        DatastoreQueryResult allRecords = new DatastoreQueryResult();
        allRecords.setCursor("new cursor");
        allRecords.setResults(recordIds);

        when(this.batchService.getAllRecords(CURSOR, KIND, LIMIT)).thenReturn(allRecords);

        ResponseEntity response = this.sut.getAllRecords(ENCODED_CURSOR, LIMIT, KIND);

        DatastoreQueryResult allRecordIds = (DatastoreQueryResult) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(3, allRecordIds.getResults().size());
        assertTrue(allRecordIds.getResults().contains("id1"));
        assertTrue(allRecordIds.getResults().contains("id2"));
        assertTrue(allRecordIds.getResults().contains("id3"));
    }

    @Test
    public void should_allowAccessToGetRecords_when_userBelongsToViewerCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getRecords", MultiRecordIds.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertTrue(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetAllKinds_when_userBelongsToCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getKinds", String.class, Integer.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertTrue(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }

    @Test
    public void should_allowAccessToGetAllRecordsFromKind_when_userBelongsToAdminGroup() throws Exception {

        Method method = this.sut.getClass().getMethod("getAllRecords", String.class, Integer.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertFalse(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }
}