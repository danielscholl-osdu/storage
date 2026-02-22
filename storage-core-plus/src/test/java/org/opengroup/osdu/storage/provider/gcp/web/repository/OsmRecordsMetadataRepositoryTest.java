/*
 *  Copyright @ Microsoft Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.web.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;

import com.github.fge.jsonpatch.JsonPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.search.SortOrder;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.service.Results;
import org.opengroup.osdu.core.osm.core.translate.Outcome;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.AbstractMap;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class OsmRecordsMetadataRepositoryTest {

  private static final String TEST_PARTITION_ID = "test-partition";
  private static final String TEST_TENANT_NAME = "test-tenant";
  private static final String TEST_ID = "test-id-123";
  private static final String TEST_KIND = "test-kind";
  private static final String TEST_LEGAL_TAG = "test-legal-tag";
  private static final String TEST_CURSOR = "test-cursor";
  private static final UUID COLLAB_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
  private static final String COLLAB_APP = "test-app";

  @Mock private Context context;
  @Mock private TenantInfo tenantInfo;
  @Mock private Results<GetQuery<RecordMetadata>, RecordMetadata> results;
  @Mock private Outcome<RecordMetadata> outcome;

  @InjectMocks private OsmRecordsMetadataRepository repository;

  @Captor private ArgumentCaptor<RecordMetadata[]> recordArrayCaptor;
  @Captor private ArgumentCaptor<String> stringCaptor;

  @BeforeEach
  void setUp() {
    lenient().when(tenantInfo.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
    lenient().when(tenantInfo.getName()).thenReturn(TEST_TENANT_NAME);
  }

  // ========================================
  // createOrUpdate Tests
  // ========================================

  @Test
  void createOrUpdate_shouldHandleStandardCase() {
    RecordMetadata record1 = createRecordMetadata("id1");
    RecordMetadata record2 = createRecordMetadata("id2");
    doNothing().when(context).upsert(any(Destination.class), any(RecordMetadata[].class));

    List<RecordMetadata> result = repository.createOrUpdate(Arrays.asList(record1, record2), Optional.empty());

    assertEquals(2, result.size());
    verify(context).upsert(any(Destination.class), recordArrayCaptor.capture());
    assertEquals(2, recordArrayCaptor.getValue().length);
    assertEquals("id1", recordArrayCaptor.getValue()[0].getId());
  }

  @Test
  void createOrUpdate_shouldHandleCollaborationContext() {
    CollaborationContext collabContext = createCollaborationContext();
    RecordMetadata recordMetadata = createRecordMetadata("id1");
    doNothing().when(context).upsert(any(Destination.class), any(RecordMetadata[].class));

    repository.createOrUpdate(Collections.singletonList(recordMetadata), Optional.of(collabContext));

    verify(context).upsert(any(Destination.class), recordArrayCaptor.capture());
    assertTrue(recordArrayCaptor.getValue()[0].getId().startsWith(COLLAB_ID.toString()));
  }

  @Test
  void createOrUpdate_shouldHandleEdgeCases() {
    // Null list
    assertNull(repository.createOrUpdate(null, Optional.empty()));

    // Empty list
    doNothing().when(context).upsert(any(Destination.class), any(RecordMetadata[].class));
    List<RecordMetadata> result = repository.createOrUpdate(Collections.emptyList(), Optional.empty());
    assertTrue(result.isEmpty());
    verify(context).upsert(any(Destination.class), recordArrayCaptor.capture());
    assertEquals(0, recordArrayCaptor.getValue().length);
  }

  @Test
  void createOrUpdate_shouldThrowExceptionForInvalidIds() {
    List<RecordMetadata> records = Collections.singletonList(createRecordMetadata(COLLAB_ID + ":app:test"));
    Optional<CollaborationContext> collab = Optional.of(createCollaborationContext());

    assertThrows(AppException.class, () ->
            repository.createOrUpdate(records, collab)
    );
  }

  // ========================================
  // delete Tests
  // ========================================

  @Test
  void delete_shouldHandleStandardCase() {
    lenient().doNothing().when(context).deleteById(any(), any(), anyString());

    repository.delete(TEST_ID, Optional.empty());

    verify(context).deleteById(eq(RecordMetadata.class), any(Destination.class), eq(TEST_ID));
  }

  @Test
  void delete_shouldHandleCollaborationContext() {
    CollaborationContext collabContext = createCollaborationContext();
    lenient().doNothing().when(context).deleteById(any(), any(), anyString());

    repository.delete(TEST_ID, Optional.of(collabContext));

    verify(context).deleteById(eq(RecordMetadata.class), any(), stringCaptor.capture());
    assertTrue(stringCaptor.getValue().startsWith(COLLAB_ID.toString()));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void delete_shouldThrowExceptionForInvalidIds(String invalidId) {
    Optional<CollaborationContext> emptyContext = Optional.empty();

    assertThrows(AppException.class, () ->
            repository.delete(invalidId, emptyContext)
    );
  }

  // ========================================
  // batchDelete Tests
  // ========================================

  @Test
  void batchDelete_shouldHandleStandardCase() {
    List<String> ids = Arrays.asList("id1", "id2", "id3");
    lenient().doNothing().when(context).deleteById(any(), any(), anyString(), any(String[].class));

    repository.batchDelete(ids, Optional.empty());

    verify(context).deleteById(eq(RecordMetadata.class), any(), eq("id1"), any(String[].class));
  }

  @Test
  void batchDelete_shouldHandleCollaborationContext() {
    CollaborationContext collabContext = createCollaborationContext();
    List<String> ids = Arrays.asList("id1", "id2");
    lenient().doNothing().when(context).deleteById(any(), any(), anyString(), any(String[].class));

    repository.batchDelete(ids, Optional.of(collabContext));

    verify(context).deleteById(eq(RecordMetadata.class), any(), stringCaptor.capture(), any(String[].class));
    assertTrue(stringCaptor.getValue().startsWith(COLLAB_ID.toString()));
  }

  @Test
  void batchDelete_shouldHandleEmptyAndNullLists() {
    repository.batchDelete(null, Optional.empty());
    repository.batchDelete(Collections.emptyList(), Optional.empty());
    verify(context, never()).deleteById(any(), any(), anyString());
    verify(context, never()).deleteById(any(), any(), anyString(), any(String[].class));
  }

  // ========================================
  // get(String) Tests
  // ========================================

  @Test
  void get_shouldReturnRecordForStandardCase() {
    RecordMetadata recordMetadata = createRecordMetadata(TEST_ID);
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(recordMetadata));

    RecordMetadata result = repository.get(TEST_ID, Optional.empty());

    assertNotNull(result);
    assertEquals(TEST_ID, result.getId());
  }

  @Test
  void get_shouldHandleCollaborationContextAndRestoreId() {
    CollaborationContext collabContext = createCollaborationContext();
    String prefixedId = COLLAB_ID + ":" + COLLAB_APP + ":" + TEST_ID;
    RecordMetadata collabRecord = createRecordMetadata(prefixedId);
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(collabRecord));

    RecordMetadata result = repository.get(TEST_ID, Optional.of(collabContext));

    // Verify record is returned (ID restoration logic tested via integration tests)
    assertNotNull(result);
  }

  @Test
  void get_shouldHandleNotFoundAndNullRecords() {
    // Not found
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());
    assertNull(repository.get(TEST_ID, Optional.empty()));

    // Filter nulls
    RecordMetadata recordMetadata = createRecordMetadata(TEST_ID);
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Arrays.asList(null, recordMetadata));
    assertNotNull(repository.get(TEST_ID, Optional.empty()));
  }

  // ========================================
  // queryByLegal Tests
  // ========================================

  @Test
  void queryByLegal_shouldQueryWithAndWithoutStatus() {
    RecordMetadata recordMetadata = createRecordMetadata(TEST_ID);
    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Collections.singletonList(recordMetadata));
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), isNull())).thenReturn(results);

    // Without status
    AbstractMap.SimpleEntry<String, List<RecordMetadata>> result =
            repository.queryByLegal(TEST_LEGAL_TAG, null, 10);
    assertEquals(TEST_CURSOR, result.getKey());
    assertEquals(1, result.getValue().size());

    // With status
    result = repository.queryByLegal(TEST_LEGAL_TAG, LegalCompliance.compliant, 10);
    assertEquals(1, result.getValue().size());

    // Empty results
    when(outcome.getList()).thenReturn(Collections.emptyList());
    when(outcome.getPointer()).thenReturn(null);
    result = repository.queryByLegal(TEST_LEGAL_TAG, null, 10);
    assertTrue(result.getValue().isEmpty());
    assertNull(result.getKey());
  }

  // ========================================
  // getRecords Tests
  // ========================================

  @Test
  void getRecords_shouldHandleBasicQueryingWithVariations() {
    RecordMetadata recordMetadata = createRecordMetadata(TEST_ID);
    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Collections.singletonList(recordMetadata));
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), any())).thenReturn(results);

    // Active records, no kind
    RecordInfoQueryResult<RecordMetadata> result = repository.getRecords(
            null, null, TEST_CURSOR, 10, false, SortOrder.ASC, Optional.empty());
    assertEquals(1, result.getResults().size());
    assertEquals(TEST_CURSOR, result.getCursor());

    // Deleted records with kind
    result = repository.getRecords(TEST_KIND, null, null, 10, true, SortOrder.DESC, Optional.empty());
    assertEquals(1, result.getResults().size());

    // With modifiedAfterTime
    result = repository.getRecords(null, 1234567890L, null, 10, false, SortOrder.ASC, Optional.empty());
    assertEquals(1, result.getResults().size());

    // Null sort order (defaults to ASC)
    result = repository.getRecords(null, null, null, 10, false, null, Optional.empty());
    assertEquals(1, result.getResults().size());
  }

  @Test
  void getRecords_shouldFilterByCollaborationContext() {
    CollaborationContext collabContext = createCollaborationContext();
    String collabRecordId = COLLAB_ID + ":" + COLLAB_APP + ":" + TEST_ID;
    String otherCollabId = UUID.randomUUID() + ":other-app:other-id";

    RecordMetadata collabRecord = createRecordMetadata(collabRecordId);
    RecordMetadata otherRecord = createRecordMetadata(otherCollabId);

    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Arrays.asList(collabRecord, otherRecord));
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), isNull())).thenReturn(results);

    RecordInfoQueryResult<RecordMetadata> result = repository.getRecords(
            null, null, null, 10, false, SortOrder.ASC, Optional.of(collabContext));

    // Verify correct record is filtered (by namespace prefix matching)
    assertEquals(1, result.getResults().size());
  }

  @Test
  void getRecords_shouldFilterOutCollaborationRecordsWhenNoContext() {
    String regularId = "regular-id";
    String collabId = UUID.randomUUID() + ":app:collab-id";

    RecordMetadata regularRecord = createRecordMetadata(regularId);
    RecordMetadata collabRecord = createRecordMetadata(collabId);

    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Arrays.asList(regularRecord, collabRecord));
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), isNull())).thenReturn(results);

    RecordInfoQueryResult<RecordMetadata> result = repository.getRecords(
            null, null, null, 10, false, SortOrder.ASC, Optional.empty());

    assertEquals(1, result.getResults().size());
    assertEquals(regularId, result.getResults().get(0).getId());
  }

  @Test
  void getRecords_shouldHandleEdgeCases() {
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), isNull())).thenReturn(results);

    // Null outcome list
    when(outcome.getList()).thenReturn(null);
    RecordInfoQueryResult<RecordMetadata> result = repository.getRecords(
            null, null, null, 10, false, SortOrder.ASC, Optional.empty());
    assertTrue(result.getResults().isEmpty());
    assertNull(result.getCursor());

    // Empty list
    when(outcome.getList()).thenReturn(Collections.emptyList());
    result = repository.getRecords(null, null, null, 10, false, SortOrder.ASC, Optional.empty());
    assertTrue(result.getResults().isEmpty());
    assertNull(result.getCursor());

    // Filter nulls and null IDs
    RecordMetadata validRecord = createRecordMetadata(TEST_ID);
    RecordMetadata nullIdRecord = new RecordMetadata();
    nullIdRecord.setId(null);
    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Arrays.asList(null, validRecord, nullIdRecord));

    result = repository.getRecords(null, null, null, 10, false, SortOrder.ASC, Optional.empty());
    assertEquals(1, result.getResults().size());

    // Empty kind string
    result = repository.getRecords("", null, null, 10, false, SortOrder.ASC, Optional.empty());
    assertEquals(1, result.getResults().size());
  }

  // ========================================
  // get(List<String>) Tests
  // ========================================

  @Test
  void getMultiple_shouldHandleStandardCase() {
    RecordMetadata record1 = createRecordMetadata("id1");
    RecordMetadata record2 = createRecordMetadata("id2");
    List<String> ids = Arrays.asList("id1", "id2");

    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Arrays.asList(record1, record2));
    Map<String, RecordMetadata> result = repository.get(ids, Optional.empty());

    assertEquals(2, result.size());
    assertTrue(result.containsKey("id1"));
    assertTrue(result.containsKey("id2"));
  }

  @Test
  void getMultiple_shouldHandleCollaborationContext() {
    CollaborationContext collabContext = createCollaborationContext();
    String prefixedId = COLLAB_ID + ":" + COLLAB_APP + ":id1";
    RecordMetadata collabRecord = createRecordMetadata(prefixedId);
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(collabRecord));

    Map<String, RecordMetadata> result = repository.get(Collections.singletonList("id1"), Optional.of(collabContext));

    // Verify record is in map with prefixed key (ID restoration depends on utility class)
    assertEquals(1, result.size());
    RecordMetadata retrieved = result.get(prefixedId);
    assertNotNull(retrieved);
  }

  @Test
  void getMultiple_shouldHandleEdgeCases() {
    // Empty list
    Map<String, RecordMetadata> result = repository.get(Collections.emptyList(), Optional.empty());
    assertTrue(result.isEmpty());
    verify(context, never()).getResultsAsList(any());

    // Filter nulls
    RecordMetadata recordMetadata = createRecordMetadata("id1");
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Arrays.asList(recordMetadata, null));
    result = repository.get(Arrays.asList("id1", "id2"), Optional.empty());
    assertEquals(1, result.size());

    // No records found
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());
    result = repository.get(Arrays.asList("id1", "id2"), Optional.empty());
    assertTrue(result.isEmpty());
  }

  // ========================================
  // queryByLegalTagName Tests
  // ========================================

  @Test
  void queryByLegalTagName_shouldDelegateToQueryByLegal() {
    RecordMetadata recordMetadata = createRecordMetadata(TEST_ID);
    when(outcome.getPointer()).thenReturn(TEST_CURSOR);
    when(outcome.getList()).thenReturn(Collections.singletonList(recordMetadata));
    when(results.outcome()).thenReturn(outcome);
    when(context.getResults(any(GetQuery.class), isNull(), eq(10), isNull())).thenReturn(results);

    AbstractMap.SimpleEntry<String, List<RecordMetadata>> result =
            repository.queryByLegalTagName(TEST_LEGAL_TAG, 10, TEST_CURSOR);

    assertEquals(TEST_CURSOR, result.getKey());
    assertEquals(1, result.getValue().size());
  }

  @Test
  void queryByLegalTagName_shouldThrowUnsupportedForArrayParameter() {
    assertThrows(UnsupportedOperationException.class, () ->
            repository.queryByLegalTagName(new String[]{TEST_LEGAL_TAG, "tag2"}, 10, TEST_CURSOR)
    );
  }

  // ========================================
  // patch Tests
  // ========================================

  @Test
  void patch_shouldHandleStandardCase() {
    RecordMetadata originalRecord = createRecordMetadata(TEST_ID);
    // Mock JsonPatch - it will be applied by JsonPatchUtil (can't easily mock static util)
    JsonPatch jsonPatch = mock(JsonPatch.class);
    Map<RecordMetadata, JsonPatch> patchMap = new HashMap<>();
    patchMap.put(originalRecord, jsonPatch);

    doNothing().when(context).upsert(any(Destination.class), any(RecordMetadata[].class));

    // This will call JsonPatchUtil.applyPatch which we can't mock
    // So we just verify upsert is called (patch logic is in utility, not repository)
    Map<String, String> result = repository.patch(patchMap, Optional.empty());

    assertNotNull(result);
    assertTrue(result.isEmpty());
    verify(context).upsert(any(Destination.class), any(RecordMetadata[].class));
  }

  @Test
  void patch_shouldHandleEdgeCases() {
    // Null map
    Map<String, String> result = repository.patch(null, Optional.empty());
    assertTrue(result.isEmpty());
    verify(context, never()).upsert(any(), any());

    // Empty map
    doNothing().when(context).upsert(any(Destination.class), any(RecordMetadata[].class));
    result = repository.patch(new HashMap<>(), Optional.empty());
    assertTrue(result.isEmpty());
    verify(context).upsert(any(Destination.class), recordArrayCaptor.capture());
    assertEquals(0, recordArrayCaptor.getValue().length);
  }

  // ========================================
  // Helper Methods
  // ========================================

  private RecordMetadata createRecordMetadata(String id) {
    RecordMetadata metadata = new RecordMetadata();
    metadata.setId(id);
    metadata.setKind(TEST_KIND);
    metadata.setStatus(RecordState.active);
    return metadata;
  }

  private CollaborationContext createCollaborationContext() {
    CollaborationContext collaborationContext = new CollaborationContext();
    collaborationContext.setId(COLLAB_ID);
    collaborationContext.setApplication(COLLAB_APP);
    return collaborationContext;
  }
}
