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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.service.Transaction;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.gcp.web.model.ReplayMetaData;
import org.opengroup.osdu.storage.request.ReplayFilter;

@ExtendWith(MockitoExtension.class)
@DisplayName("OsmReplayRepository Tests")
class OsmReplayRepositoryTest {

  private static final String TEST_REPLAY_ID = "replay-123";
  private static final String TEST_KIND = "test-kind";
  private static final String TEST_PARTITION_ID = "test-partition";
  private static final String TEST_TENANT_NAME = "test-tenant";
  private static final String TEST_ID = "id-123";
  private static final String TEST_OPERATION = "CREATE";
  private static final Long TEST_TOTAL_RECORDS = 100L;
  private static final Long TEST_PROCESSED_RECORDS = 50L;
  private static final String TEST_STATE = "RUNNING";
  private static final String TEST_ELAPSED_TIME = "5m";

  @Mock
  private Context context;

  @Mock
  private TenantInfo tenantInfo;

  @Mock
  private Transaction transaction;

  @InjectMocks
  private OsmReplayRepository repository;

  @Captor
  private ArgumentCaptor<GetQuery<ReplayMetaData>> queryCaptor;

  @Captor
  private ArgumentCaptor<Destination> destinationCaptor;

  @Captor
  private ArgumentCaptor<ReplayMetaData> replayMetaDataCaptor;

  @BeforeEach
  void setUp() {
    lenient().when(tenantInfo.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
    lenient().when(tenantInfo.getName()).thenReturn(TEST_TENANT_NAME);
  }

  // ========================================
  // getReplayStatusByReplayId Tests
  // ========================================

  @Nested
  @DisplayName("getReplayStatusByReplayId Tests")
  class GetReplayStatusByReplayIdTests {

    @Test
    @DisplayName("Should return list of ReplayMetaDataDTO when results exist")
    void getReplayStatusByReplayId_WithResults_ReturnsListOfDTOs() {
      // Arrange
      ReplayMetaData metadata1 = createReplayMetaData(TEST_ID + "1", TEST_REPLAY_ID, TEST_KIND + "1");
      ReplayMetaData metadata2 = createReplayMetaData(TEST_ID + "2", TEST_REPLAY_ID, TEST_KIND + "2");
      List<ReplayMetaData> mockResults = Arrays.asList(metadata1, metadata2);

      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(mockResults);

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.size());
      assertEquals(TEST_ID + "1", result.get(0).getId());
      assertEquals(TEST_ID + "2", result.get(1).getId());
      assertEquals(TEST_REPLAY_ID, result.get(0).getReplayId());
      assertEquals(TEST_REPLAY_ID, result.get(1).getReplayId());

      verify(context).getResultsAsList(queryCaptor.capture());
      GetQuery<ReplayMetaData> capturedQuery = queryCaptor.getValue();
      assertNotNull(capturedQuery);
    }

    @Test
    @DisplayName("Should return empty list when no results found")
    void getReplayStatusByReplayId_NoResults_ReturnsEmptyList() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should return empty list when context returns null")
    void getReplayStatusByReplayId_NullResults_ReturnsEmptyList() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(null);

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should construct query with correct destination")
    void getReplayStatusByReplayId_ConstructsCorrectQuery() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      verify(context).getResultsAsList(queryCaptor.capture());
      GetQuery<ReplayMetaData> query = queryCaptor.getValue();

      Destination destination = query.getDestination();
      assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
      assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
      assertEquals("ReplayStatus", destination.getKind().getName());
    }

    @Test
    @DisplayName("Should handle single result")
    void getReplayStatusByReplayId_SingleResult_ReturnsSingletonList() {
      // Arrange
      ReplayMetaData metadata = createReplayMetaData(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(metadata));

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals(TEST_ID, result.get(0).getId());
    }
  }

  // ========================================
  // getReplayStatusByKindAndReplayId Tests
  // ========================================

  @Nested
  @DisplayName("getReplayStatusByKindAndReplayId Tests")
  class GetReplayStatusByKindAndReplayIdTests {

    @Test
    @DisplayName("Should return first ReplayMetaDataDTO when results exist")
    void getReplayStatusByKindAndReplayId_WithResults_ReturnsFirstDTO() {
      // Arrange
      ReplayMetaData metadata1 = createReplayMetaData(TEST_ID + "1", TEST_REPLAY_ID, TEST_KIND);
      ReplayMetaData metadata2 = createReplayMetaData(TEST_ID + "2", TEST_REPLAY_ID, TEST_KIND);
      List<ReplayMetaData> mockResults = Arrays.asList(metadata1, metadata2);

      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(mockResults);

      // Act
      ReplayMetaDataDTO result = repository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(TEST_ID + "1", result.getId());
      assertEquals(TEST_REPLAY_ID, result.getReplayId());
      assertEquals(TEST_KIND, result.getKind());

      verify(context).getResultsAsList(queryCaptor.capture());
      GetQuery<ReplayMetaData> capturedQuery = queryCaptor.getValue();
      assertNotNull(capturedQuery);
    }

    @Test
    @DisplayName("Should return null when no results found")
    void getReplayStatusByKindAndReplayId_NoResults_ReturnsNull() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      ReplayMetaDataDTO result = repository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);

      // Assert
      assertNull(result);
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should return null when context returns null")
    void getReplayStatusByKindAndReplayId_NullResults_ReturnsNull() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(null);

      // Act
      ReplayMetaDataDTO result = repository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);

      // Assert
      assertNull(result);
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should construct query with AND condition")
    void getReplayStatusByKindAndReplayId_ConstructsQueryWithAndCondition() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      repository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);

      // Assert
      verify(context).getResultsAsList(queryCaptor.capture());
      GetQuery<ReplayMetaData> query = queryCaptor.getValue();

      assertNotNull(query.getWhere());
      Destination destination = query.getDestination();
      assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
    }

    @Test
    @DisplayName("Should return single result when only one exists")
    void getReplayStatusByKindAndReplayId_SingleResult_ReturnsDTO() {
      // Arrange
      ReplayMetaData metadata = createReplayMetaData(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.singletonList(metadata));

      // Act
      ReplayMetaDataDTO result = repository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(TEST_ID, result.getId());
      assertEquals(TEST_REPLAY_ID, result.getReplayId());
      assertEquals(TEST_KIND, result.getKind());
    }
  }

  // ========================================
  // save Tests
  // ========================================

  @Nested
  @DisplayName("save Tests")
  class SaveTests {

    @Test
    @DisplayName("Should successfully save and commit transaction")
    void save_Success_CommitsTransaction() {
      // Arrange
      ReplayMetaDataDTO dto = createReplayMetaDataDTO(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
      doNothing().when(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      doNothing().when(transaction).commitIfActive();

      // Act
      ReplayMetaDataDTO result = repository.save(dto);

      // Assert
      assertNotNull(result);
      assertEquals(dto.getId(), result.getId());
      assertEquals(dto.getReplayId(), result.getReplayId());

      verify(context).beginTransaction(destinationCaptor.capture());
      verify(context).upsert(any(Destination.class), replayMetaDataCaptor.capture());
      verify(transaction).commitIfActive();
      verify(transaction, never()).rollbackIfActive();

      // Verify destination
      Destination destination = destinationCaptor.getValue();
      assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
      assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());

      // Verify converted data
      ReplayMetaData capturedMetadata = replayMetaDataCaptor.getValue();
      assertEquals(dto.getId(), capturedMetadata.getId());
      assertEquals(dto.getReplayId(), capturedMetadata.getReplayId());
      assertEquals(dto.getKind(), capturedMetadata.getKind());
    }

    @Test
    @DisplayName("Should rollback and rethrow exception on upsert failure")
    void save_UpsertFails_RollsBackAndThrowsException() {
      // Arrange
      ReplayMetaDataDTO dto = createReplayMetaDataDTO(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);

      RuntimeException expectedException = new RuntimeException("Upsert failed");
      doThrow(expectedException).when(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      doNothing().when(transaction).rollbackIfActive();

      // Act & Assert
      RuntimeException thrown = assertThrows(RuntimeException.class, () -> repository.save(dto));

      assertEquals(expectedException, thrown);
      assertEquals("Upsert failed", thrown.getMessage());

      verify(context).beginTransaction(any(Destination.class));
      verify(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      verify(transaction, never()).commitIfActive();
      verify(transaction).rollbackIfActive();
    }

    @Test
    @DisplayName("Should rollback and rethrow exception on commit failure")
    void save_CommitFails_RollsBackAndThrowsException() {
      // Arrange
      ReplayMetaDataDTO dto = createReplayMetaDataDTO(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
      doNothing().when(context).upsert(any(Destination.class), any(ReplayMetaData.class));

      RuntimeException expectedException = new RuntimeException("Commit failed");
      doThrow(expectedException).when(transaction).commitIfActive();
      doNothing().when(transaction).rollbackIfActive();

      // Act & Assert
      RuntimeException thrown = assertThrows(RuntimeException.class, () -> repository.save(dto));

      assertEquals(expectedException, thrown);
      verify(transaction).commitIfActive();
      verify(transaction).rollbackIfActive();
    }

    @Test
    @DisplayName("Should handle exception when transaction is null")
    void save_ExceptionWithNullTransaction_ThrowsException() {
      // Arrange
      ReplayMetaDataDTO dto = createReplayMetaDataDTO(TEST_ID, TEST_REPLAY_ID, TEST_KIND);
      when(context.beginTransaction(any(Destination.class))).thenReturn(null);

      RuntimeException expectedException = new RuntimeException("Transaction is null");
      doThrow(expectedException).when(context).upsert(any(Destination.class), any(ReplayMetaData.class));

      // Act & Assert
      RuntimeException thrown = assertThrows(RuntimeException.class, () -> repository.save(dto));

      assertEquals(expectedException, thrown);
      verify(context).beginTransaction(any(Destination.class));
    }

    @Test
    @DisplayName("Should save DTO with all fields populated")
    void save_AllFieldsPopulated_SavesSuccessfully() {
      // Arrange
      Date startDate = new Date();
      ReplayFilter filter = new ReplayFilter();

      ReplayMetaDataDTO dto = ReplayMetaDataDTO.builder()
              .id(TEST_ID)
              .replayId(TEST_REPLAY_ID)
              .kind(TEST_KIND)
              .operation(TEST_OPERATION)
              .totalRecords(TEST_TOTAL_RECORDS)
              .startedAt(startDate)
              .filter(filter)
              .processedRecords(TEST_PROCESSED_RECORDS)
              .state(TEST_STATE)
              .elapsedTime(TEST_ELAPSED_TIME)
              .build();

      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
      doNothing().when(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      doNothing().when(transaction).commitIfActive();

      // Act
      ReplayMetaDataDTO result = repository.save(dto);

      // Assert
      assertNotNull(result);
      verify(context).upsert(any(Destination.class), replayMetaDataCaptor.capture());

      ReplayMetaData captured = replayMetaDataCaptor.getValue();
      assertEquals(TEST_ID, captured.getId());
      assertEquals(TEST_REPLAY_ID, captured.getReplayId());
      assertEquals(TEST_KIND, captured.getKind());
      assertEquals(TEST_OPERATION, captured.getOperation());
      assertEquals(TEST_TOTAL_RECORDS, captured.getTotalRecords());
      assertEquals(startDate, captured.getStartedAt());
      assertEquals(filter, captured.getFilter());
      assertEquals(TEST_PROCESSED_RECORDS, captured.getProcessedRecords());
      assertEquals(TEST_STATE, captured.getState());
      assertEquals(TEST_ELAPSED_TIME, captured.getElapsedTime());
    }

    @Test
    @DisplayName("Should save DTO with minimal fields")
    void save_MinimalFields_SavesSuccessfully() {
      // Arrange
      ReplayMetaDataDTO dto = ReplayMetaDataDTO.builder()
              .id(TEST_ID)
              .replayId(TEST_REPLAY_ID)
              .build();

      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
      doNothing().when(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      doNothing().when(transaction).commitIfActive();

      // Act
      ReplayMetaDataDTO result = repository.save(dto);

      // Assert
      assertNotNull(result);
      assertEquals(TEST_ID, result.getId());
      assertEquals(TEST_REPLAY_ID, result.getReplayId());

      verify(context).upsert(any(Destination.class), replayMetaDataCaptor.capture());
      ReplayMetaData captured = replayMetaDataCaptor.getValue();
      assertEquals(TEST_ID, captured.getId());
      assertEquals(TEST_REPLAY_ID, captured.getReplayId());
      assertNull(captured.getKind());
      assertNull(captured.getOperation());
    }
  }

  // ========================================
  // Converter Methods Tests (Indirect)
  // ========================================

  @Nested
  @DisplayName("Converter Methods Tests")
  class ConverterMethodsTests {

    @Test
    @DisplayName("Should convert ReplayMetaData to DTO with all fields")
    void toReplayMetaDataDTO_AllFields_ConvertsCorrectly() {
      // Arrange
      Date startDate = new Date();
      ReplayFilter filter = new ReplayFilter();
      ReplayMetaData metadata = ReplayMetaData.builder()
              .id(TEST_ID)
              .replayId(TEST_REPLAY_ID)
              .kind(TEST_KIND)
              .operation(TEST_OPERATION)
              .totalRecords(TEST_TOTAL_RECORDS)
              .startedAt(startDate)
              .filter(filter)
              .processedRecords(TEST_PROCESSED_RECORDS)
              .state(TEST_STATE)
              .elapsedTime(TEST_ELAPSED_TIME)
              .build();

      when(context.getResultsAsList(any(GetQuery.class)))
              .thenReturn(Collections.singletonList(metadata));

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.size());
      ReplayMetaDataDTO dto = result.get(0);
      assertEquals(TEST_ID, dto.getId());
      assertEquals(TEST_REPLAY_ID, dto.getReplayId());
      assertEquals(TEST_KIND, dto.getKind());
      assertEquals(TEST_OPERATION, dto.getOperation());
      assertEquals(TEST_TOTAL_RECORDS, dto.getTotalRecords());
      assertEquals(startDate, dto.getStartedAt());
      assertEquals(filter, dto.getFilter());
      assertEquals(TEST_PROCESSED_RECORDS, dto.getProcessedRecords());
      assertEquals(TEST_STATE, dto.getState());
      assertEquals(TEST_ELAPSED_TIME, dto.getElapsedTime());
    }

    @Test
    @DisplayName("Should convert DTO to ReplayMetaData with all fields")
    void toReplayMetaData_AllFields_ConvertsCorrectly() {
      // Arrange
      Date startDate = new Date();
      ReplayFilter filter = new ReplayFilter();
      ReplayMetaDataDTO dto = ReplayMetaDataDTO.builder()
              .id(TEST_ID)
              .replayId(TEST_REPLAY_ID)
              .kind(TEST_KIND)
              .operation(TEST_OPERATION)
              .totalRecords(TEST_TOTAL_RECORDS)
              .startedAt(startDate)
              .filter(filter)
              .processedRecords(TEST_PROCESSED_RECORDS)
              .state(TEST_STATE)
              .elapsedTime(TEST_ELAPSED_TIME)
              .build();

      when(context.beginTransaction(any(Destination.class))).thenReturn(transaction);
      doNothing().when(context).upsert(any(Destination.class), any(ReplayMetaData.class));
      doNothing().when(transaction).commitIfActive();

      // Act
      repository.save(dto);

      // Assert
      verify(context).upsert(any(Destination.class), replayMetaDataCaptor.capture());
      ReplayMetaData metadata = replayMetaDataCaptor.getValue();

      assertEquals(TEST_ID, metadata.getId());
      assertEquals(TEST_REPLAY_ID, metadata.getReplayId());
      assertEquals(TEST_KIND, metadata.getKind());
      assertEquals(TEST_OPERATION, metadata.getOperation());
      assertEquals(TEST_TOTAL_RECORDS, metadata.getTotalRecords());
      assertEquals(startDate, metadata.getStartedAt());
      assertEquals(filter, metadata.getFilter());
      assertEquals(TEST_PROCESSED_RECORDS, metadata.getProcessedRecords());
      assertEquals(TEST_STATE, metadata.getState());
      assertEquals(TEST_ELAPSED_TIME, metadata.getElapsedTime());
    }

    @Test
    @DisplayName("Should handle null fields in conversion")
    void toReplayMetaDataDTO_NullFields_ConvertsCorrectly() {
      // Arrange
      ReplayMetaData metadata = ReplayMetaData.builder()
              .id(TEST_ID)
              .replayId(TEST_REPLAY_ID)
              .build();

      when(context.getResultsAsList(any(GetQuery.class)))
              .thenReturn(Collections.singletonList(metadata));

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      assertNotNull(result);
      assertEquals(1, result.size());
      ReplayMetaDataDTO dto = result.get(0);
      assertEquals(TEST_ID, dto.getId());
      assertEquals(TEST_REPLAY_ID, dto.getReplayId());
      assertNull(dto.getKind());
      assertNull(dto.getOperation());
      assertNull(dto.getTotalRecords());
      assertNull(dto.getStartedAt());
      assertNull(dto.getFilter());
      assertNull(dto.getProcessedRecords());
      assertNull(dto.getState());
      assertNull(dto.getElapsedTime());
    }
  }

  // ========================================
  // Edge Cases and Integration Tests
  // ========================================

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle empty replay ID")
    void getReplayStatusByReplayId_EmptyReplayId_HandlesGracefully() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      List<ReplayMetaDataDTO> result = repository.getReplayStatusByReplayId("");

      // Assert
      assertNotNull(result);
      assertTrue(result.isEmpty());
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should handle empty kind")
    void getReplayStatusByKindAndReplayId_EmptyKind_HandlesGracefully() {
      // Arrange
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());

      // Act
      ReplayMetaDataDTO result = repository.getReplayStatusByKindAndReplayId("", TEST_REPLAY_ID);

      // Assert
      assertNull(result);
      verify(context).getResultsAsList(any(GetQuery.class));
    }

    @Test
    @DisplayName("Should verify destination is built correctly")
    void getDestination_BuildsCorrectly() {
      // Arrange & Act
      when(context.getResultsAsList(any(GetQuery.class))).thenReturn(Collections.emptyList());
      repository.getReplayStatusByReplayId(TEST_REPLAY_ID);

      // Assert
      verify(context).getResultsAsList(queryCaptor.capture());
      Destination destination = queryCaptor.getValue().getDestination();

      assertNotNull(destination);
      assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
      assertNotNull(destination.getNamespace());
      assertEquals(TEST_TENANT_NAME, destination.getNamespace().getName());
      assertNotNull(destination.getKind());
      assertEquals("ReplayStatus", destination.getKind().getName());
    }
  }

  // ========================================
  // Helper Methods
  // ========================================

  private ReplayMetaData createReplayMetaData(String id, String replayId, String kind) {
    return ReplayMetaData.builder()
            .id(id)
            .replayId(replayId)
            .kind(kind)
            .operation(TEST_OPERATION)
            .totalRecords(TEST_TOTAL_RECORDS)
            .startedAt(new Date())
            .filter(new ReplayFilter())
            .processedRecords(TEST_PROCESSED_RECORDS)
            .state(TEST_STATE)
            .elapsedTime(TEST_ELAPSED_TIME)
            .build();
  }

  private ReplayMetaDataDTO createReplayMetaDataDTO(String id, String replayId, String kind) {
    return ReplayMetaDataDTO.builder()
            .id(id)
            .replayId(replayId)
            .kind(kind)
            .operation(TEST_OPERATION)
            .totalRecords(TEST_TOTAL_RECORDS)
            .startedAt(new Date())
            .filter(new ReplayFilter())
            .processedRecords(TEST_PROCESSED_RECORDS)
            .state(TEST_STATE)
            .elapsedTime(TEST_ELAPSED_TIME)
            .build();
  }
}
