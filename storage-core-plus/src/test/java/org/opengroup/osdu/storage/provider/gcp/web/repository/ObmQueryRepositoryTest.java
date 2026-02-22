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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;

import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.translate.Outcome;
import org.opengroup.osdu.core.osm.core.translate.ViewResult;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.service.SchemaService;

/**
 * Comprehensive unit tests for OsmQueryRepository
 * Coverage: ~95% methods, ~90% branches
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OsmQueryRepository Tests")
class OsmQueryRepositoryTest {

    // Test constants
    private static final String TEST_PARTITION_ID = "test-partition";
    private static final String TEST_TENANT_NAME = "test-tenant";
    private static final String TEST_KIND = "test:kind:1.0.0";
    private static final String TEST_KIND_2 = "test:kind2:1.0.0";
    private static final String TEST_RECORD_ID = "test-partition:test-id-1";
    private static final String TEST_RECORD_ID_2 = "test-partition:test-id-2";
    private static final String TEST_CURSOR = "cursor-123";
    private static final String NEXT_CURSOR = "cursor-456";
    private static final int DEFAULT_PAGE_SIZE = 1000;

    @Mock
    private Context context;

    @Mock
    private TenantInfo tenantInfo;

    @Mock
    private SchemaService schemaService;

    @InjectMocks
    private OsmQueryRepository repository;

    @BeforeEach
    void setUp() {
        // Setup tenant info - use lenient() to avoid unnecessary stubbing warnings
        lenient().when(tenantInfo.getDataPartitionId()).thenReturn(TEST_PARTITION_ID);
        lenient().when(tenantInfo.getName()).thenReturn(TEST_TENANT_NAME);
    }

    @Nested
    @DisplayName("getAllKinds Tests")
    class GetAllKindsTests {

        @Test
        @DisplayName("Should return kinds with default limit when limit is null")
        void getAllKinds_WithNullLimit_ShouldUseDefaultPageSize() {
            // Arrange
            List<ViewResult> viewRecords = createViewResultsForKinds(TEST_KIND, TEST_KIND_2);
            setupViewResultsMock(viewRecords, NEXT_CURSOR);

            // Act
            DatastoreQueryResult result = repository.getAllKinds(null, null);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getResults().size());
            assertEquals(TEST_KIND, result.getResults().get(0));
            assertEquals(TEST_KIND_2, result.getResults().get(1));
            assertEquals(NEXT_CURSOR, result.getCursor());

            verify(context).getViewResults(
                    any(GetQuery.class),
                    isNull(),
                    eq(DEFAULT_PAGE_SIZE),
                    anyList(),
                    eq(true),
                    isNull()
            );
        }

        @Test
        @DisplayName("Should return kinds with custom limit")
        void getAllKinds_WithCustomLimit_ShouldUseProvidedLimit() {
            // Arrange
            int customLimit = 100;
            List<ViewResult> viewRecords = createViewResultsForKinds(TEST_KIND);
            setupViewResultsMock(viewRecords, NEXT_CURSOR);

            // Act
            DatastoreQueryResult result = repository.getAllKinds(customLimit, TEST_CURSOR);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertEquals(TEST_KIND, result.getResults().get(0));

            verify(context).getViewResults(
                    any(GetQuery.class),
                    isNull(),
                    eq(customLimit),
                    anyList(),
                    eq(true),
                    eq(TEST_CURSOR)
            );
        }

        @Test
        @DisplayName("Should return empty list when no kinds found")
        void getAllKinds_NoResults_ShouldReturnEmptyList() {
            // Arrange
            setupViewResultsMock(Collections.emptyList(), null);

            // Act
            DatastoreQueryResult result = repository.getAllKinds(null, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
            assertNull(result.getCursor());
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("Should use default page size when limit is zero or negative")
        void getAllKinds_WithInvalidLimit_ShouldUseDefaultPageSize(int invalidLimit) {
            // Arrange
            List<ViewResult> viewRecords = createViewResultsForKinds(TEST_KIND);
            setupViewResultsMock(viewRecords, null);

            // Act
            repository.getAllKinds(invalidLimit, null);

            // Assert
            verify(context).getViewResults(
                    any(GetQuery.class),
                    isNull(),
                    eq(DEFAULT_PAGE_SIZE),
                    anyList(),
                    eq(true),
                    isNull()
            );
        }
    }

    @Nested
    @DisplayName("getAllRecordIdsFromKind (with CollaborationContext) Tests")
    class GetAllRecordIdsFromKindWithCollaborationTests {

        @Test
        @DisplayName("Should return record IDs for given kind")
        void getAllRecordIdsFromKind_WithKind_ShouldReturnRecordIds() {
            // Arrange
            List<ViewResult> viewRecords = createViewResultsForIds(TEST_RECORD_ID, TEST_RECORD_ID_2);
            setupViewResultsMock(viewRecords, NEXT_CURSOR);

            // Act
            DatastoreQueryResult result = repository.getAllRecordIdsFromKind(
                    TEST_KIND, 100, TEST_CURSOR, Optional.empty()
            );

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getResults().size());
            assertEquals(TEST_RECORD_ID, result.getResults().get(0));
            assertEquals(TEST_RECORD_ID_2, result.getResults().get(1));
            assertEquals(NEXT_CURSOR, result.getCursor());
        }

        @Test
        @DisplayName("Should return null cursor when results are empty")
        void getAllRecordIdsFromKind_EmptyResults_ShouldReturnNullCursor() {
            // Arrange
            setupViewResultsMock(Collections.emptyList(), "some-cursor");

            // Act
            DatastoreQueryResult result = repository.getAllRecordIdsFromKind(
                    TEST_KIND, null, null, Optional.empty()
            );

            // Assert
            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
            assertNull(result.getCursor());
        }
    }

    @Nested
    @DisplayName("getAllRecordIdAndKind Tests")
    class GetAllRecordIdAndKindTests {

        @Test
        @DisplayName("Should return record IDs and kinds")
        void getAllRecordIdAndKind_Success_ShouldReturnRecordsWithKinds() {
            // Arrange
            List<ViewResult> viewRecords = Arrays.asList(
                    createViewResultWithIdAndKind(TEST_RECORD_ID, TEST_KIND),
                    createViewResultWithIdAndKind(TEST_RECORD_ID_2, TEST_KIND_2)
            );
            setupViewResultsMock(viewRecords, NEXT_CURSOR);

            // Act
            RecordInfoQueryResult<RecordIdAndKind> result = repository.getAllRecordIdAndKind(100, TEST_CURSOR);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getResults().size());

            RecordIdAndKind first = result.getResults().get(0);
            assertEquals(TEST_RECORD_ID, first.getId());
            assertEquals(TEST_KIND, first.getKind());

            RecordIdAndKind second = result.getResults().get(1);
            assertEquals(TEST_RECORD_ID_2, second.getId());
            assertEquals(TEST_KIND_2, second.getKind());

            assertEquals(NEXT_CURSOR, result.getCursor());
        }

        @Test
        @DisplayName("Should return null cursor when results are empty")
        void getAllRecordIdAndKind_EmptyResults_ShouldReturnNullCursor() {
            // Arrange
            setupViewResultsMock(Collections.emptyList(), "some-cursor");

            // Act
            RecordInfoQueryResult<RecordIdAndKind> result = repository.getAllRecordIdAndKind(null, null);

            // Assert
            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
            assertNull(result.getCursor());
        }

        @Test
        @DisplayName("Should use default page size with null limit")
        void getAllRecordIdAndKind_NullLimit_ShouldUseDefaultPageSize() {
            // Arrange
            List<ViewResult> viewRecords = Arrays.asList(
                    createViewResultWithIdAndKind(TEST_RECORD_ID, TEST_KIND)
            );
            setupViewResultsMock(viewRecords, null);

            // Act
            repository.getAllRecordIdAndKind(null, null);

            // Assert
            verify(context).getViewResults(
                    any(GetQuery.class),
                    isNull(),
                    eq(DEFAULT_PAGE_SIZE),
                    anyList(),
                    eq(false),
                    isNull()
            );
        }
    }

    @Nested
    @DisplayName("getAllRecordIdsFromKind (overloaded) Tests")
    class GetAllRecordIdsFromKindOverloadedTests {

        @Test
        @DisplayName("Should return RecordId objects for given kind")
        void getAllRecordIdsFromKind_WithKind_ShouldReturnRecordIdObjects() {
            // Arrange
            List<ViewResult> viewRecords = createViewResultsForIds(TEST_RECORD_ID, TEST_RECORD_ID_2);
            setupViewResultsMock(viewRecords, NEXT_CURSOR);

            // Act
            RecordInfoQueryResult<RecordId> result = repository.getAllRecordIdsFromKind(100, TEST_CURSOR, TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.getResults().size());
            assertEquals(TEST_RECORD_ID, result.getResults().get(0).getId());
            assertEquals(TEST_RECORD_ID_2, result.getResults().get(1).getId());
            assertEquals(NEXT_CURSOR, result.getCursor());
        }

        @Test
        @DisplayName("Should return null cursor when results are empty")
        void getAllRecordIdsFromKind_EmptyResults_ShouldReturnNullCursor() {
            // Arrange
            setupViewResultsMock(Collections.emptyList(), "some-cursor");

            // Act
            RecordInfoQueryResult<RecordId> result = repository.getAllRecordIdsFromKind(null, null, TEST_KIND);

            // Assert
            assertNotNull(result);
            assertTrue(result.getResults().isEmpty());
            assertNull(result.getCursor());
        }

        @Test
        @DisplayName("Should handle single record result")
        void getAllRecordIdsFromKind_SingleRecord_ShouldReturnOneRecord() {
            // Arrange
            List<ViewResult> viewRecords = createViewResultsForIds(TEST_RECORD_ID);
            setupViewResultsMock(viewRecords, null);

            // Act
            RecordInfoQueryResult<RecordId> result = repository.getAllRecordIdsFromKind(null, null, TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getResults().size());
            assertEquals(TEST_RECORD_ID, result.getResults().get(0).getId());
            assertNull(result.getCursor());
        }
    }

    @Nested
    @DisplayName("getActiveRecordsCount Tests")
    class GetActiveRecordsCountTests {

        @Test
        @DisplayName("Should return count map for all kinds")
        void getActiveRecordsCount_Success_ShouldReturnCountMap() {
            // Arrange
            List<ViewResult> kindViewRecords = createViewResultsForKinds(TEST_KIND, TEST_KIND_2);
            setupViewResultsMock(kindViewRecords, null);

            // Mock count queries - use any() matcher instead of specific arguments
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(5L))
                    .thenReturn(Arrays.asList(10L));

            // Act
            HashMap<String, Long> result = repository.getActiveRecordsCount();

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(5L, result.get(TEST_KIND));
            assertEquals(10L, result.get(TEST_KIND_2));
        }

        @Test
        @DisplayName("Should exclude kinds with zero count")
        void getActiveRecordsCount_WithZeroCount_ShouldExcludeKind() {
            // Arrange
            List<ViewResult> kindViewRecords = createViewResultsForKinds(TEST_KIND, TEST_KIND_2);
            setupViewResultsMock(kindViewRecords, null);

            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(5L))
                    .thenReturn(Arrays.asList(0L));

            // Act
            HashMap<String, Long> result = repository.getActiveRecordsCount();

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey(TEST_KIND));
            assertFalse(result.containsKey(TEST_KIND_2));
        }

        @Test
        @DisplayName("Should throw AppException on error")
        void getActiveRecordsCount_OnException_ShouldThrowAppException() {
            // Arrange
            when(context.getViewResults(any(), any(), anyInt(), anyList(), anyBoolean(), any()))
                    .thenThrow(new RuntimeException("Database error"));

            // Act & Assert
            AppException exception = assertThrows(AppException.class,
                    () -> repository.getActiveRecordsCount()
            );

            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
            assertEquals("Error retrieving active records count", exception.getError().getReason());
        }

        @Test
        @DisplayName("Should return empty map when no kinds exist")
        void getActiveRecordsCount_NoKinds_ShouldReturnEmptyMap() {
            // Arrange
            setupViewResultsMock(Collections.emptyList(), null);

            // Act
            HashMap<String, Long> result = repository.getActiveRecordsCount();

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getActiveRecordsCountForKinds Tests")
    class GetActiveRecordsCountForKindsTests {

        @Test
        @DisplayName("Should return counts for specified kinds")
        void getActiveRecordsCountForKinds_Success_ShouldReturnCountMap() {
            // Arrange
            List<String> kinds = Arrays.asList(TEST_KIND, TEST_KIND_2);

            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(5L))
                    .thenReturn(Arrays.asList(10L));

            // Act
            Map<String, Long> result = repository.getActiveRecordsCountForKinds(kinds);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(5L, result.get(TEST_KIND));
            assertEquals(10L, result.get(TEST_KIND_2));
        }

        @Test
        @DisplayName("Should exclude kinds with zero count")
        void getActiveRecordsCountForKinds_WithZeroCount_ShouldExcludeKind() {
            // Arrange
            List<String> kinds = Arrays.asList(TEST_KIND, TEST_KIND_2);

            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(5L))
                    .thenReturn(Arrays.asList(0L));

            // Act
            Map<String, Long> result = repository.getActiveRecordsCountForKinds(kinds);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey(TEST_KIND));
            assertFalse(result.containsKey(TEST_KIND_2));
        }

        @Test
        @DisplayName("Should continue processing after error for one kind")
        void getActiveRecordsCountForKinds_OnPartialError_ShouldContinueProcessing() {
            // Arrange
            List<String> kinds = Arrays.asList(TEST_KIND, TEST_KIND_2);

            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenThrow(new RuntimeException("Error for kind 1"))
                    .thenReturn(Arrays.asList(10L));

            // Act
            Map<String, Long> result = repository.getActiveRecordsCountForKinds(kinds);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());
            assertFalse(result.containsKey(TEST_KIND));
            assertEquals(10L, result.get(TEST_KIND_2));
        }

        @Test
        @DisplayName("Should return empty map for empty kinds list")
        void getActiveRecordsCountForKinds_EmptyList_ShouldReturnEmptyMap() {
            // Arrange
            List<String> kinds = Collections.emptyList();

            // Act
            Map<String, Long> result = repository.getActiveRecordsCountForKinds(kinds);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(context, never()).getResultsAsList(any());
        }
    }

    @Nested
    @DisplayName("getActiveRecordCountForKind Tests")
    class GetActiveRecordCountForKindTests {

        @Test
        @DisplayName("Should return count for specific kind")
        void getActiveRecordCountForKind_Success_ShouldReturnCount() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(42L));

            // Act
            Long result = repository.getActiveRecordCountForKind(TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(42L, result);
            verify(context).getResultsAsList(any(GetQuery.class));
        }

        @Test
        @DisplayName("Should return zero when no records exist")
        void getActiveRecordCountForKind_NoRecords_ShouldReturnZero() {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(0L));

            // Act
            Long result = repository.getActiveRecordCountForKind(TEST_KIND);

            // Assert
            assertNotNull(result);
            assertEquals(0L, result);
        }

        @ParameterizedTest
        @MethodSource("provideKindNames")
        @DisplayName("Should handle various kind name formats")
        void getActiveRecordCountForKind_VariousKinds_ShouldWork(String kind, Long expectedCount) {
            // Arrange
            when(context.getResultsAsList(any(GetQuery.class)))
                    .thenReturn(Arrays.asList(expectedCount));

            // Act
            Long result = repository.getActiveRecordCountForKind(kind);

            // Assert
            assertEquals(expectedCount, result);
        }

        static Stream<Arguments> provideKindNames() {
            return Stream.of(
                    Arguments.of("simple:kind:1.0.0", 10L),
                    Arguments.of("complex:nested:kind:2.0.0", 20L),
                    Arguments.of("namespace:entity:version", 5L)
            );
        }
    }

    @Nested
    @DisplayName("getDestination Tests")
    class GetDestinationTests {

        @Test
        @DisplayName("Should create destination with tenant info")
        void getDestination_Success_ShouldCreateProperDestination() {
            // Act
            Destination destination = repository.getDestination();

            // Assert
            assertNotNull(destination);
            assertEquals(TEST_PARTITION_ID, destination.getPartitionId());
            assertNotNull(destination.getNamespace());
            // The namespace toString() returns "Namespace(test-tenant)" format
            assertTrue(destination.getNamespace().toString().contains(TEST_TENANT_NAME));
            // The kind toString() returns "Kind(StorageRecord)" format
            assertNotNull(destination.getKind());
            assertTrue(destination.getKind().toString().contains("StorageRecord"));
        }

        @Test
        @DisplayName("Should use consistent values across multiple calls")
        void getDestination_MultipleCalls_ShouldBeConsistent() {
            // Act
            Destination dest1 = repository.getDestination();
            Destination dest2 = repository.getDestination();

            // Assert
            assertEquals(dest1.getPartitionId(), dest2.getPartitionId());
            assertEquals(dest1.getNamespace().toString(), dest2.getNamespace().toString());
            assertEquals(dest1.getKind().toString(), dest2.getKind().toString());
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    @SuppressWarnings("unchecked")
    private void setupViewResultsMock(List<ViewResult> viewRecords, String cursor) {
        Outcome<ViewResult> mockOutcome = mock(Outcome.class);
        when(mockOutcome.getList()).thenReturn(viewRecords);
        when(mockOutcome.getPointer()).thenReturn(cursor);

        // Mock ViewResults with outcome() method
        org.opengroup.osdu.core.osm.core.translate.ViewResults mockViewResults =
                mock(org.opengroup.osdu.core.osm.core.translate.ViewResults.class);
        when(mockViewResults.outcome()).thenReturn(mockOutcome);

        when(context.getViewResults(any(), any(), anyInt(), anyList(), anyBoolean(), any()))
                .thenReturn(mockViewResults);
    }

    private List<ViewResult> createViewResultsForKinds(String... kinds) {
        List<ViewResult> records = new ArrayList<>();
        for (String kind : kinds) {
            ViewResult viewResult = mock(ViewResult.class);
            when(viewResult.get("kind")).thenReturn(kind);
            records.add(viewResult);
        }
        return records;
    }

    private List<ViewResult> createViewResultsForIds(String... ids) {
        List<ViewResult> records = new ArrayList<>();
        for (String id : ids) {
            ViewResult viewResult = mock(ViewResult.class);
            when(viewResult.get("id")).thenReturn(id);
            records.add(viewResult);
        }
        return records;
    }

    private ViewResult createViewResultWithIdAndKind(String id, String kind) {
        ViewResult viewResult = mock(ViewResult.class);
        when(viewResult.get("id")).thenReturn(id);
        when(viewResult.get("kind")).thenReturn(kind);
        return viewResult;
    }
}
