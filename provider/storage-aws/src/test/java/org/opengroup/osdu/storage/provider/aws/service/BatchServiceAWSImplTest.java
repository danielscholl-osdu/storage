package org.opengroup.osdu.storage.provider.aws.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.util.Collections;
import java.util.Optional;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.aws.exceptions.InvalidCursorException;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
class BatchServiceAWSImplTest {
    @InjectMocks
    private BatchServiceAWSImpl service;

    @Mock
    private IQueryRepository queryRepository;

    @Mock
    private StorageAuditLogger auditLogger;

    private static final String CURSOR = "cursor";
    private static final Integer LIMIT = 10;
    private static final String KIND = "kind";

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    @Test
    void getAllKinds_shouldReturnResultFromRepository_whenQuerySucceeds() {
        DatastoreQueryResult expectedResult = new DatastoreQueryResult();
        when(queryRepository.getAllKinds(LIMIT, CURSOR)).thenReturn(expectedResult);

        DatastoreQueryResult result = service.getAllKinds(CURSOR, LIMIT);

        assertEquals(expectedResult, result);
        verify(auditLogger).readAllKindsSuccess(expectedResult.getResults());
        verify(queryRepository).getAllKinds(LIMIT, CURSOR);
    }

    @Test
    void getAllKinds_shouldThrowAppException_whenInvalidCursorExceptionOccurs() {
        when(queryRepository.getAllKinds(LIMIT, CURSOR)).thenThrow(new InvalidCursorException("The requested cursor does not exist or is invalid"));

        AppException exception = assertThrows(AppException.class, () -> {
            service.getAllKinds(CURSOR, LIMIT);
        });

        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
        assertEquals("The requested cursor does not exist or is invalid", exception.getMessage());
    }
    @Test
    void getAllKinds_shouldThrowInternalErrorException_whenGeneralExceptionOccurs() {
        when(queryRepository.getAllKinds(LIMIT, CURSOR)).thenThrow(new RuntimeException("General exception"));

        AppException exception = assertThrows(AppException.class, () -> {
            service.getAllKinds(CURSOR, LIMIT);
        });

        // Assuming that getInternalErrorException method sets a specific status code, replace with actual value
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
    }
    @Test
    void getAllRecords_shouldReturnResultFromRepository_whenQuerySucceedsWithNonEmptyResults() {
        DatastoreQueryResult expectedResult = new DatastoreQueryResult();
        expectedResult.setResults(Collections.singletonList("result1"));


        when(queryRepository.getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty())).thenReturn(expectedResult);

        DatastoreQueryResult result = service.getAllRecords(CURSOR, KIND, LIMIT, Optional.empty());

        assertEquals(expectedResult, result);
        verify(auditLogger).readAllRecordsOfGivenKindSuccess(Collections.singletonList(KIND));
        verify(queryRepository).getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty());
    }

@Test
void getAllRecords_shouldReturnResultFromRepository_whenQuerySucceedsWithEmptyResults() {
    DatastoreQueryResult expectedResult = new DatastoreQueryResult();
    expectedResult.setResults(Collections.emptyList());

    when(queryRepository.getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty())).thenReturn(expectedResult);

    DatastoreQueryResult result = service.getAllRecords(CURSOR, KIND, LIMIT, Optional.empty());

    assertEquals(expectedResult, result);
    verify(auditLogger, never()).readAllRecordsOfGivenKindSuccess(any());
    verify(queryRepository).getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty());
}

@Test
void getAllRecords_shouldThrowAppException_whenInvalidCursorExceptionOccurs() {

    when(queryRepository.getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty())).thenThrow(new InvalidCursorException("The requested cursor does not exist or is invalid"));

    AppException exception = assertThrows(AppException.class, () -> {
        service.getAllRecords(CURSOR, KIND, LIMIT, Optional.empty());
    });

    assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
    assertEquals("The requested cursor does not exist or is invalid", exception.getMessage());
}

@Test
void getAllRecords_shouldThrowInternalErrorException_whenGeneralExceptionOccurs() {
    when(queryRepository.getAllRecordIdsFromKind(KIND, LIMIT, CURSOR, Optional.empty())).thenThrow(new RuntimeException("General exception"));

    AppException exception = assertThrows(AppException.class, () -> {
        service.getAllRecords(CURSOR, KIND, LIMIT, Optional.empty());
    });

    // Assuming that getInternalErrorException method sets a specific status code, replace with actual value
    assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
}

}
