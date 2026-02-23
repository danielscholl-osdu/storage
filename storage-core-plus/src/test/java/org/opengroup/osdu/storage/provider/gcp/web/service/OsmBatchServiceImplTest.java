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
package org.opengroup.osdu.storage.provider.gcp.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class OsmBatchServiceImplTest {

    @Mock
    private IQueryRepository queryRepository;

    @Mock
    private StorageAuditLogger auditLogger;

    @InjectMocks
    private OsmBatchServiceImpl osmBatchService;

    @Captor
    private ArgumentCaptor<List<String>> kindsCaptor;

    private static final String TEST_CURSOR = "test-cursor-123";
    private static final String TEST_KIND = "test-kind";
    private static final Integer TEST_LIMIT = 100;

    @BeforeEach
    void setUp() {
        reset(queryRepository, auditLogger);
    }

    // ==================== getAllKinds Tests ====================

    @Test
    void getAllKinds_shouldDelegateToRepositoryAndLogResults() {
        // Arrange - standard result set
        List<String> kinds = Arrays.asList("kind1", "kind2", "kind3");
        DatastoreQueryResult expectedResult = createDatastoreQueryResult(kinds, "next-cursor");
        when(queryRepository.getAllKinds(TEST_LIMIT, TEST_CURSOR)).thenReturn(expectedResult);

        // Act
        DatastoreQueryResult actualResult = osmBatchService.getAllKinds(TEST_CURSOR, TEST_LIMIT);

        // Assert - correct result and delegation
        assertNotNull(actualResult);
        assertEquals(expectedResult, actualResult);
        assertEquals(kinds, actualResult.getResults());
        assertEquals("next-cursor", actualResult.getCursor());
        verify(queryRepository).getAllKinds(TEST_LIMIT, TEST_CURSOR);
        verify(auditLogger).readAllKindsSuccess(kinds);
        verifyNoMoreInteractions(queryRepository, auditLogger);

        // Reset for edge cases
        reset(queryRepository, auditLogger);

        // Arrange - empty results
        List<String> emptyKinds = Collections.emptyList();
        DatastoreQueryResult emptyResult = createDatastoreQueryResult(emptyKinds, null);
        when(queryRepository.getAllKinds(TEST_LIMIT, TEST_CURSOR)).thenReturn(emptyResult);

        // Act
        DatastoreQueryResult emptyActual = osmBatchService.getAllKinds(TEST_CURSOR, TEST_LIMIT);

        // Assert - empty results still logged
        assertTrue(emptyActual.getResults().isEmpty());
        verify(auditLogger).readAllKindsSuccess(emptyKinds);

        // Reset for null cursor test
        reset(queryRepository, auditLogger);

        // Arrange - null cursor
        List<String> singleKind = Arrays.asList("kind1");
        DatastoreQueryResult nullCursorResult = createDatastoreQueryResult(singleKind, null);
        when(queryRepository.getAllKinds(TEST_LIMIT, null)).thenReturn(nullCursorResult);

        // Act
        DatastoreQueryResult nullCursorActual = osmBatchService.getAllKinds(null, TEST_LIMIT);

        // Assert - null cursor handled correctly
        assertNotNull(nullCursorActual);
        assertEquals(1, nullCursorActual.getResults().size());
        verify(queryRepository).getAllKinds(TEST_LIMIT, null);
        verify(auditLogger).readAllKindsSuccess(singleKind);
    }

    @Test
    void getAllKinds_shouldHandleLargeResultSets() {
        // Arrange - 1000 kinds
        List<String> largeKindsList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeKindsList.add("kind" + i);
        }
        DatastoreQueryResult expectedResult = createDatastoreQueryResult(largeKindsList, "next-cursor");
        when(queryRepository.getAllKinds(1000, TEST_CURSOR)).thenReturn(expectedResult);

        // Act
        DatastoreQueryResult actualResult = osmBatchService.getAllKinds(TEST_CURSOR, 1000);

        // Assert - large result set handled correctly
        assertNotNull(actualResult);
        assertEquals(1000, actualResult.getResults().size());
        verify(queryRepository).getAllKinds(1000, TEST_CURSOR);
        verify(auditLogger).readAllKindsSuccess(largeKindsList);
    }

    // ==================== getAllRecords Tests ====================

    @Test
    void getAllRecords_shouldDelegateToRepositoryAndLogNonEmptyResults() {
        // Arrange - standard result with records
        List<String> recordIds = Arrays.asList("record1", "record2", "record3");
        DatastoreQueryResult expectedResult = createDatastoreQueryResult(recordIds, "next-cursor");
        Optional<CollaborationContext> collaborationContext = Optional.empty();
        when(queryRepository.getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, TEST_CURSOR, collaborationContext))
                .thenReturn(expectedResult);

        // Act
        DatastoreQueryResult actualResult = osmBatchService.getAllRecords(
                TEST_CURSOR, TEST_KIND, TEST_LIMIT, collaborationContext);

        // Assert - correct result and audit logging
        assertNotNull(actualResult);
        assertEquals(expectedResult, actualResult);
        assertEquals(recordIds, actualResult.getResults());
        verify(queryRepository).getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, TEST_CURSOR, collaborationContext);
        verify(auditLogger).readAllRecordsOfGivenKindSuccess(kindsCaptor.capture());

        List<String> capturedKinds = kindsCaptor.getValue();
        assertEquals(1, capturedKinds.size(), "Should log singleton list");
        assertEquals(TEST_KIND, capturedKinds.get(0), "Should log correct kind");
        verifyNoMoreInteractions(queryRepository, auditLogger);

        // Reset for empty results test
        reset(queryRepository, auditLogger);

        // Arrange - empty results
        List<String> emptyRecords = Collections.emptyList();
        DatastoreQueryResult emptyResult = createDatastoreQueryResult(emptyRecords, null);
        when(queryRepository.getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, TEST_CURSOR, collaborationContext))
                .thenReturn(emptyResult);

        // Act
        DatastoreQueryResult emptyActual = osmBatchService.getAllRecords(
                TEST_CURSOR, TEST_KIND, TEST_LIMIT, collaborationContext);

        // Assert - empty results do NOT trigger audit logging
        assertTrue(emptyActual.getResults().isEmpty());
        verify(queryRepository).getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, TEST_CURSOR, collaborationContext);
        verify(auditLogger, never()).readAllRecordsOfGivenKindSuccess(any());
        verifyNoMoreInteractions(queryRepository, auditLogger);

        // Reset for null cursor test
        reset(queryRepository, auditLogger);

        // Arrange - null cursor
        List<String> singleRecord = Arrays.asList("record1");
        DatastoreQueryResult nullCursorResult = createDatastoreQueryResult(singleRecord, null);
        when(queryRepository.getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, null, collaborationContext))
                .thenReturn(nullCursorResult);

        // Act
        DatastoreQueryResult nullCursorActual = osmBatchService.getAllRecords(
                null, TEST_KIND, TEST_LIMIT, collaborationContext);

        // Assert - null cursor handled correctly
        assertNotNull(nullCursorActual);
        assertEquals(1, nullCursorActual.getResults().size());
        verify(queryRepository).getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, null, collaborationContext);
        verify(auditLogger).readAllRecordsOfGivenKindSuccess(any());
    }

    @Test
    void getAllRecords_shouldPassCollaborationContextCorrectly() {
        // Arrange
        CollaborationContext context = new CollaborationContext();
        Optional<CollaborationContext> collaborationContext = Optional.of(context);
        List<String> recordIds = Arrays.asList("record1");
        DatastoreQueryResult expectedResult = createDatastoreQueryResult(recordIds, null);
        when(queryRepository.getAllRecordIdsFromKind(TEST_KIND, TEST_LIMIT, TEST_CURSOR, collaborationContext))
                .thenReturn(expectedResult);

        // Act
        DatastoreQueryResult actualResult = osmBatchService.getAllRecords(
                TEST_CURSOR, TEST_KIND, TEST_LIMIT, collaborationContext);

        // Assert - context passed correctly
        assertNotNull(actualResult);

        ArgumentCaptor<String> kindCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> cursorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Optional<CollaborationContext>> contextCaptor = ArgumentCaptor.forClass(Optional.class);

        verify(queryRepository).getAllRecordIdsFromKind(
                kindCaptor.capture(),
                limitCaptor.capture(),
                cursorCaptor.capture(),
                contextCaptor.capture());

        assertEquals(TEST_KIND, kindCaptor.getValue());
        assertEquals(TEST_LIMIT, limitCaptor.getValue());
        assertEquals(TEST_CURSOR, cursorCaptor.getValue());
        assertTrue(contextCaptor.getValue().isPresent());
        assertEquals(context, contextCaptor.getValue().get());
        verify(auditLogger).readAllRecordsOfGivenKindSuccess(any());
    }

    @Test
    void getAllRecords_shouldHandleLargeResultSets() {
        // Arrange - 5000 records
        List<String> largeRecordList = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            largeRecordList.add("record" + i);
        }
        DatastoreQueryResult expectedResult = createDatastoreQueryResult(largeRecordList, "next-cursor");
        Optional<CollaborationContext> collaborationContext = Optional.empty();
        when(queryRepository.getAllRecordIdsFromKind(TEST_KIND, 5000, TEST_CURSOR, collaborationContext))
                .thenReturn(expectedResult);

        // Act
        DatastoreQueryResult actualResult = osmBatchService.getAllRecords(
                TEST_CURSOR, TEST_KIND, 5000, collaborationContext);

        // Assert - large result set handled correctly
        assertNotNull(actualResult);
        assertEquals(5000, actualResult.getResults().size());
        verify(queryRepository).getAllRecordIdsFromKind(TEST_KIND, 5000, TEST_CURSOR, collaborationContext);
        verify(auditLogger).readAllRecordsOfGivenKindSuccess(any());
    }

    // ==================== Helper Methods ====================

    private DatastoreQueryResult createDatastoreQueryResult(List<String> results, String cursor) {
        DatastoreQueryResult result = new DatastoreQueryResult();
        result.setResults(results);
        result.setCursor(cursor);
        return result;
    }
}
