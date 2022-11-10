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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.google.common.collect.Lists;
import org.mockito.Spy;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.di.SchemaEndpointsConfig;
import org.opengroup.osdu.storage.service.BatchService;
import org.opengroup.osdu.storage.util.EncodeDecode;
import org.springframework.http.ResponseEntity;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class QueryApiTest {
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());
    private final String COLLABORATION_DIRECTIVES = "id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=TestApp";
    
    @Mock
    private BatchService batchService;

    @Mock
    private SchemaEndpointsConfig schemaEndpointsConfig;

    @Mock
    private CollaborationContextFactory collaborationContextFactory;

    @Spy
    private EncodeDecode encodeDecode;

    @InjectMocks
    private QueryApi sut;

    @Test
    public void should_returnHttp200_when_gettingRecordsSuccessfullyWithCollaborationContext() {
        MultiRecordIds input = new MultiRecordIds();
        input.setRecords(Lists.newArrayList("id1", "id2"));

        MultiRecordInfo output = new MultiRecordInfo();
        List<Record> validRecords = getValidRecords();

        output.setRecords(validRecords);

        when(this.batchService.getMultipleRecords(input, COLLABORATION_CONTEXT)).thenReturn(output);
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);


        ResponseEntity response = this.sut.getRecords(COLLABORATION_DIRECTIVES, input);

        MultiRecordInfo records = (MultiRecordInfo) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertNull(records.getInvalidRecords());
        assertNull(records.getRetryRecords());
        assertEquals(2, records.getRecords().size());
        assertTrue(records.getRecords().get(0).toString().contains("id1"));
        assertTrue(records.getRecords().get(1).toString().contains("id2"));
    }

    @Test
    public void should_returnHttp200_when_gettingRecordsSuccessfullyWithoutCollaborationContext() {
        MultiRecordIds input = new MultiRecordIds();
        input.setRecords(Lists.newArrayList("id1", "id2"));

        MultiRecordInfo output = new MultiRecordInfo();
        List<Record> validRecords = getValidRecords();

        output.setRecords(validRecords);

        when(this.batchService.getMultipleRecords(input, Optional.empty())).thenReturn(output);
        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());


        ResponseEntity response = this.sut.getRecords(COLLABORATION_DIRECTIVES, input);

        MultiRecordInfo records = (MultiRecordInfo) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertNull(records.getInvalidRecords());
        assertNull(records.getRetryRecords());
        assertEquals(2, records.getRecords().size());
        assertTrue(records.getRecords().get(0).toString().contains("id1"));
        assertTrue(records.getRecords().get(1).toString().contains("id2"));
    }

    private List<Record> getValidRecords() {
        List<Record> validRecords = new ArrayList<>();

        Record record1 = new Record();
        record1.setId("id1");

        Record record2 = new Record();
        record2.setId("id2");

        validRecords.add(record1);
        validRecords.add(record2);
        return validRecords;
    }

    @Test
    public void should_returnHttp200_when_schemaApiIsEnabled_and_gettingAllKindsSuccessfully() {
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
    public void should_returnHttp200_when_gettingAllKinds() {
        final String CURSOR = "any cursor";
        final String ENCODED_CURSOR = Base64.getEncoder().encodeToString("any cursor".getBytes());
        final int LIMIT = 10;
        List<String> kinds = new ArrayList<String>();
        DatastoreQueryResult allKinds = new DatastoreQueryResult();
        allKinds.setCursor("new cursor");
        allKinds.setResults(kinds);
        when(this.schemaEndpointsConfig.isDisabled()).thenReturn(true);
        when(this.batchService.getAllKinds(CURSOR, LIMIT)).thenReturn(allKinds);
        ResponseEntity response = this.sut.getKinds(ENCODED_CURSOR, LIMIT);
        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
    }

    @Test
    public void should_returnHttp200_when_gettingAllRecordsFromKindSuccessfullyWithCollaborationContext() {
        final String CURSOR = "any cursor";
        final String ENCODED_CURSOR = Base64.getEncoder().encodeToString("any cursor".getBytes());

        final String KIND = "any kind";
        final int LIMIT = 10;

        List<String> recordIds = Arrays.asList("id1", "id2", "id3");

        DatastoreQueryResult allRecords = new DatastoreQueryResult();
        allRecords.setCursor("new cursor");
        allRecords.setResults(recordIds);

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        when(this.batchService.getAllRecords(CURSOR, KIND, LIMIT, COLLABORATION_CONTEXT)).thenReturn(allRecords);

        ResponseEntity response = this.sut.getAllRecords(COLLABORATION_DIRECTIVES, ENCODED_CURSOR, LIMIT, KIND);

        DatastoreQueryResult allRecordIds = (DatastoreQueryResult) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(3, allRecordIds.getResults().size());
        assertTrue(allRecordIds.getResults().contains("id1"));
        assertTrue(allRecordIds.getResults().contains("id2"));
        assertTrue(allRecordIds.getResults().contains("id3"));
    }

    @Test
    public void should_returnHttp200_when_gettingAllRecordsFromKindSuccessfullyWithoutCollaborationContext() {
        final String CURSOR = "any cursor";
        final String ENCODED_CURSOR = Base64.getEncoder().encodeToString("any cursor".getBytes());

        final String KIND = "any kind";
        final int LIMIT = 10;

        List<String> recordIds = Arrays.asList("id1", "id2", "id3");

        DatastoreQueryResult allRecords = new DatastoreQueryResult();
        allRecords.setCursor("new cursor");
        allRecords.setResults(recordIds);

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());
        when(this.batchService.getAllRecords(CURSOR, KIND, LIMIT, Optional.empty())).thenReturn(allRecords);

        ResponseEntity response = this.sut.getAllRecords(COLLABORATION_DIRECTIVES, ENCODED_CURSOR, LIMIT, KIND);

        DatastoreQueryResult allRecordIds = (DatastoreQueryResult) response.getBody();

        assertEquals(HttpStatus.SC_OK, response.getStatusCodeValue());
        assertEquals(3, allRecordIds.getResults().size());
        assertTrue(allRecordIds.getResults().contains("id1"));
        assertTrue(allRecordIds.getResults().contains("id2"));
        assertTrue(allRecordIds.getResults().contains("id3"));
    }

    @Test
    public void should_allowAccessToGetRecords_when_userBelongsToViewerCreatorOrAdminGroups() throws Exception {

        Method method = this.sut.getClass().getMethod("getRecords", String.class, MultiRecordIds.class);
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

        Method method = this.sut.getClass().getMethod("getAllRecords",String.class, String.class, Integer.class, String.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertFalse(annotation.value().contains(StorageRole.VIEWER));
        assertFalse(annotation.value().contains(StorageRole.CREATOR));
        assertTrue(annotation.value().contains(StorageRole.ADMIN));
    }
}
