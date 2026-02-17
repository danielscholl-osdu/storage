/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;

@ExtendWith(MockitoExtension.class)
public class OsmRecordsMetadataRepositoryTest {

  @Mock
  private Context context;

  @Mock
  private TenantInfo tenantInfo;

  @InjectMocks
  private OsmRecordsMetadataRepository repository;

  private static final String RECORD_ID = "tenant:kind:id";
  private static final String RECORD_ID_2 = "tenant:kind:id2";
  private static final String COLLAB_ID = UUID.randomUUID().toString();
  private static final String COLLAB_APP = "app";
  private CollaborationContext collaborationContext;

  @BeforeEach
  public void setup() {
    collaborationContext = CollaborationContext.builder()
        .id(UUID.fromString(COLLAB_ID))
        .application(COLLAB_APP)
        .build();
    when(tenantInfo.getDataPartitionId()).thenReturn("tenant");
    when(tenantInfo.getName()).thenReturn("tenant-name");
  }

  @Test
  public void createOrUpdate_withCollaborationContext_shouldSaveWithPrefixedId() {
    RecordMetadata record = new RecordMetadata();
    record.setId(RECORD_ID);
    List<RecordMetadata> records = Collections.singletonList(record);

    repository.createOrUpdate(records, Optional.of(collaborationContext));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<RecordMetadata[]> captor = ArgumentCaptor.forClass(RecordMetadata[].class);
    verify(context).upsert(any(Destination.class), captor.capture());

    RecordMetadata[] savedRecords = captor.getValue();
    assertEquals(1, savedRecords.length);
    String expectedId = COLLAB_ID + RECORD_ID;
    assertEquals(expectedId, savedRecords[0].getId());
  }

  @Test
  public void createOrUpdate_withoutCollaborationContext_shouldSaveWithOriginalId() {
    RecordMetadata record = new RecordMetadata();
    record.setId(RECORD_ID);
    List<RecordMetadata> records = Collections.singletonList(record);

    repository.createOrUpdate(records, Optional.empty());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<RecordMetadata[]> captor = ArgumentCaptor.forClass(RecordMetadata[].class);
    verify(context).upsert(any(Destination.class), captor.capture());

    RecordMetadata[] savedRecords = captor.getValue();
    assertEquals(1, savedRecords.length);
    assertEquals(RECORD_ID, savedRecords[0].getId());
  }

  @Test
  public void delete_withCollaborationContext_shouldDeletePrefixedId() {
    repository.delete(RECORD_ID, Optional.of(collaborationContext));

    String expectedId = COLLAB_ID + RECORD_ID;
    verify(context).deleteById(eq(RecordMetadata.class), any(Destination.class), eq(expectedId));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void get_withCollaborationContext_shouldReturnRecordWithOriginalId() {
    String prefixedId = COLLAB_ID + RECORD_ID;
    RecordMetadata storedRecord = new RecordMetadata();
    storedRecord.setId(prefixedId);

    // Mock OSM return
    when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(storedRecord));

    RecordMetadata result = repository.get(RECORD_ID, Optional.of(collaborationContext));

    assertNotNull(result);
    assertEquals(RECORD_ID, result.getId());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void getBatch_withCollaborationContext_shouldReturnMapWithComposedKeysAndOriginalIdsInMetadata() {
      // Map keys should be composed IDs
      // RecordMetadata objects should have original IDs restored
      String prefixedId1 = COLLAB_ID + RECORD_ID;
      String prefixedId2 = COLLAB_ID + RECORD_ID_2;

      RecordMetadata r1 = new RecordMetadata(); r1.setId(prefixedId1);
      RecordMetadata r2 = new RecordMetadata(); r2.setId(prefixedId2);

      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Arrays.asList(r1, r2));

      Map<String, RecordMetadata> results = repository.get(Arrays.asList(RECORD_ID, RECORD_ID_2), Optional.of(collaborationContext));
      
      // Keys should be composed IDs (callers use composeIdWithNamespace for lookup)
      assertNotNull(results.get(prefixedId1), "Map should have composed ID as key");
      assertNotNull(results.get(prefixedId2), "Map should have composed ID as key");
      
      // Metadata objects should have original IDs restored
      assertEquals(RECORD_ID, results.get(prefixedId1).getId(), "Metadata should have original ID");
      assertEquals(RECORD_ID_2, results.get(prefixedId2).getId(), "Metadata should have original ID");
  }
  
  @Test
  @SuppressWarnings("unchecked")
  public void getBatch_withoutCollaborationContext_shouldReturnMapWithOriginalKeys() {
      RecordMetadata r1 = new RecordMetadata(); r1.setId(RECORD_ID);
      RecordMetadata r2 = new RecordMetadata(); r2.setId(RECORD_ID_2);

      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Arrays.asList(r1, r2));

      Map<String, RecordMetadata> results = repository.get(Arrays.asList(RECORD_ID, RECORD_ID_2), Optional.empty());
      
      // Without collaboration context, keys and IDs should both be original
      assertNotNull(results.get(RECORD_ID));
      assertNotNull(results.get(RECORD_ID_2));
      assertEquals(RECORD_ID, results.get(RECORD_ID).getId());
      assertEquals(RECORD_ID_2, results.get(RECORD_ID_2).getId());
  }
  
  @Test
  public void batchDelete_withCollaborationContext_shouldDeleteWithPrefixedIds() {
      List<String> ids = Arrays.asList(RECORD_ID, RECORD_ID_2);
      
      repository.batchDelete(ids, Optional.of(collaborationContext));
      
      String expectedId1 = COLLAB_ID + RECORD_ID;
      String expectedId2 = COLLAB_ID + RECORD_ID_2;
      
      // Verify deleteById is called with composed IDs
      verify(context).deleteById(
          eq(RecordMetadata.class), 
          any(Destination.class), 
          eq(expectedId1), 
          eq(new String[]{expectedId2})
      );
  }
}
