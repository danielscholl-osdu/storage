
// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutRequest;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.aws.exceptions.InvalidCursorException;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.util.JsonPatchUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class RecordsMetadataRepositoryTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private RecordsMetadataRepositoryImpl repo;

    @Mock
    private DynamoDBQueryHelperFactory queryHelperFactory;

    @Mock
    private DynamoDBQueryHelperV2 queryHelper;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private JaxRsDpsLog logger;

    @Captor
    ArgumentCaptor<Set<String>> idsCaptor;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(10);

    @BeforeEach
    public void setUp() {
        openMocks(this);
        when(queryHelperFactory.getQueryHelperForPartition(any(DpsHeaders.class), any(), any()))
        .thenReturn(queryHelper);
        when(workerThreadPool.getThreadPool()).thenReturn(threadPool);
    }

    @Test
    void testQueryByLegalTagName() throws InvalidCursorException, UnsupportedEncodingException {
        String legalTagName = "legalTagName";
        int limit = 500;
        String cursor = "cursor";
        LegalTagAssociationDoc doc = mock(LegalTagAssociationDoc.class);
        when(doc.getRecordId()).thenReturn("id");
        List<LegalTagAssociationDoc> docs = new ArrayList<>();
        docs.add(doc);
        QueryPageResult<LegalTagAssociationDoc> result = mock(QueryPageResult.class);
        result.results = docs;
        when(queryHelper.queryPage(eq(LegalTagAssociationDoc.class), any(), eq(limit), eq(cursor))).thenReturn(result);
        repo.queryByLegalTagName(legalTagName, limit, cursor);

        assertNotNull(result);

    }

    @Test
    void testPatch() throws JsonMappingException, JsonProcessingException, IOException {
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        JsonPatch patch = JsonPatch.fromJson(new ObjectMapper().readTree("[{ \"op\": \"replace\", \"path\": \"/kind\", \"value\": \"newKind\" }]"));

        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("recordId");
        recordMetadata.setKind("recordKind");
        Legal legal = new Legal();
        legal.setLegaltags(new HashSet<>(Arrays.asList("legalTag1", "legalTag2")));
        recordMetadata.setLegal(legal);
        recordMetadata.setStatus(RecordState.active);
        recordMetadata.setUser("recordUser");
        
        RecordMetadata newRecordMetadata = new RecordMetadata();
        newRecordMetadata.setId("newRecordId");
        newRecordMetadata.setKind("newRecordKind");
        newRecordMetadata.setLegal(legal);
        newRecordMetadata.setStatus(RecordState.active);
        newRecordMetadata.setUser("newRecordUser");
        
        MockedStatic<JsonPatchUtil> mocked = Mockito.mockStatic(JsonPatchUtil.class);
        try {
            mocked.when(() -> JsonPatchUtil.applyPatch(patch, RecordMetadata.class, recordMetadata)).thenReturn(newRecordMetadata);

            jsonPatchPerRecord.put(recordMetadata, patch);
            Map<String, String> result = repo.patch(jsonPatchPerRecord, Optional.empty());

            verify(queryHelper, Mockito.times(2)).batchSave(any());
            assertTrue(result.isEmpty());

        } finally {
            mocked.close(); 
        }
    }

    @Test
    void testQueryByLegalTagNameThrowsException() throws InvalidCursorException, UnsupportedEncodingException {
        String legalTagName = "legalTagName";
        int limit = 500;
        String cursor = "cursor";
        when(queryHelper.queryPage(any(),any(), eq(limit), eq(cursor))).thenThrow(UnsupportedEncodingException.class);
        assertThrows(AppException.class, () -> repo.queryByLegalTagName(legalTagName, limit, cursor));
    }

    @Test
    void testPatchIfJsonPatchPerRecordNull() {
        // Arrange
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = null;

        // Act
        Map<String, String> result = repo.patch(jsonPatchPerRecord, Optional.empty());

        // Assert
        assertTrue(result.isEmpty());
    }

    private List<RecordMetadata> generateRecordsMetadata() {
        // Arrange
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("opendes:id:15706318658560");
        recordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        recordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        recordMetadata.setStatus(recordStatus);

        String user = "test-user";
        recordMetadata.setUser(user);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(recordMetadata);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(recordMetadata.getId());
        expectedRmd.setKind(recordMetadata.getKind());
        expectedRmd.setLegaltags(recordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(recordMetadata.getStatus().toString());
        expectedRmd.setUser(recordMetadata.getUser());
        expectedRmd.setMetadata(recordMetadata);

        return recordsMetadata;
    }

    @Test
    void createRecordMetadata() {
        List<RecordMetadata> recordsMetadata = generateRecordsMetadata();

        when(queryHelper.batchSave(any())).thenReturn(new ArrayList<>());

        // Act
        repo.createOrUpdate(recordsMetadata, Optional.empty());

        // Assert
        Mockito.verify(queryHelper, Mockito.times(2)).batchSave(any());
    }

    @Test
    void shouldThrowAppException_whenSavingRecordMetadataFails() {
        List<RecordMetadata> recordsMetadata = generateRecordsMetadata();
        DynamoDBMapper.FailedBatch failedBatch = new DynamoDBMapper.FailedBatch();
        failedBatch.setException(new Exception());
        failedBatch.setUnprocessedItems(Collections.singletonMap("SomeTable",
                                                                 Collections.singletonList(new WriteRequest().withPutRequest(new PutRequest()
                                                                                                                                 .addItemEntry("some-key",
                                                                                                                                               new AttributeValue().withS("some-value")
                                                                                                                                 )))));
        when(queryHelper.batchSave(any())).thenReturn(Collections.singletonList(failedBatch));
        Optional<CollaborationContext> collaborationContext = Optional.empty();

        assertThrows(AppException.class, () -> repo.createOrUpdate(recordsMetadata, collaborationContext));
    }

    @Test
    void getRecordMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";

        RecordMetadata expectedRecordMetadata = new RecordMetadata();
        expectedRecordMetadata.setId(id);
        expectedRecordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        expectedRecordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        expectedRecordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        expectedRecordMetadata.setStatus(recordStatus);

        String user = "test-user";
        expectedRecordMetadata.setUser(user);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(expectedRecordMetadata.getId());
        expectedRmd.setKind(expectedRecordMetadata.getKind());
        expectedRmd.setLegaltags(expectedRecordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(expectedRecordMetadata.getStatus().toString());
        expectedRmd.setUser(expectedRecordMetadata.getUser());
        expectedRmd.setMetadata(expectedRecordMetadata);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.anyString()))
                .thenReturn(expectedRmd);

        // Act
        RecordMetadata recordMetadata = repo.get(id, Optional.empty());

        // Assert
        Assert.assertEquals(recordMetadata, expectedRecordMetadata);
    }

    @Test
    void getRecordsMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";
        List<String> ids = new ArrayList<>();
        ids.add(id);
        for (int i = 0; i < 105; ++i) {
            ids.add(String.format("%s:%03d", id, i));
        }

        RecordMetadata expectedRecordMetadata = new RecordMetadata();
        expectedRecordMetadata.setId(id);
        expectedRecordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        expectedRecordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        expectedRecordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        expectedRecordMetadata.setStatus(recordStatus);

        String user = "test-user";
        expectedRecordMetadata.setUser(user);

        Map<String, RecordMetadata> expectedRecordsMetadata = new HashMap<>();
        expectedRecordsMetadata.put(id, expectedRecordMetadata);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(expectedRecordMetadata.getId());
        expectedRmd.setKind(expectedRecordMetadata.getKind());
        expectedRmd.setLegaltags(expectedRecordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(expectedRecordMetadata.getStatus().toString());
        expectedRmd.setUser(expectedRecordMetadata.getUser());
        expectedRmd.setMetadata(expectedRecordMetadata);

        Mockito.when(queryHelper.batchLoadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.any()))
                .thenReturn(Collections.singletonList(expectedRmd));

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        // Act
        Map<String, RecordMetadata> recordsMetadata = repo.get(ids, Optional.empty());

        // Assert
        Assertions.assertEquals(recordsMetadata, expectedRecordsMetadata);

        verify(queryHelper, times(2)).batchLoadByPrimaryKey(eq(RecordMetadataDoc.class), idsCaptor.capture());

        Set<String> allIdsCalled = idsCaptor.getAllValues().stream().flatMap(Set::stream).collect(Collectors.toSet());
        assertEquals(ids.size(), allIdsCalled.size());
        for (String expectedId : ids) {
            assertTrue(allIdsCalled.contains(expectedId));
        }
    }

    @Test
    void deleteRecordMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";
        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("opendes:id:15706318658560");
        recordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);
        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        recordMetadata.setLegal(recordLegal);
        expectedRmd.setMetadata(recordMetadata);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.doNothing().when(queryHelper).deleteByPrimaryKey(RecordMetadataDoc.class, id);
        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.anyString()))
                .thenReturn(expectedRmd);

        // Act
        repo.delete(id, Optional.empty());

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).deleteByPrimaryKey(RecordMetadataDoc.class, id);
    }

}
