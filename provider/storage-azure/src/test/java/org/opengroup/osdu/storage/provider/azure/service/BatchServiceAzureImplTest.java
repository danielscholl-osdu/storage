package org.opengroup.osdu.storage.provider.azure.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;

import java.util.Collections;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchServiceAzureImplTest {
    @InjectMocks
    BatchServiceAzureImpl sut;
    @Mock
    private StorageAuditLogger auditLogger;
    @Mock
    private IQueryRepository queryRepository;
    @Mock
    private JaxRsDpsLog logger;

    @Test
    void getAllKinds_shouldReturnAllKinds() {
        DatastoreQueryResult expectedResult = new DatastoreQueryResult();
        when(queryRepository.getAllKinds(10, "cursor")).thenReturn(expectedResult);

        DatastoreQueryResult actualResult = sut.getAllKinds("cursor", 10);

        verify(auditLogger, times(1)).readAllKindsSuccess(expectedResult.getResults());
        verify(queryRepository, times(1)).getAllKinds(10, "cursor");
    }

    @Test
    void getAllRecords_shouldReturnAllRecords() {
        DatastoreQueryResult expectedResult = new DatastoreQueryResult();
        expectedResult.setResults(asList("result1", "result2"));
        when(queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty())).thenReturn(expectedResult);

        DatastoreQueryResult actualResult = sut.getAllRecords("cursor", "kind", 10, Optional.empty());

        verify(auditLogger, times(1)).readAllRecordsOfGivenKindSuccess(Collections.singletonList("kind"));
        verify(queryRepository, times(1)).getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty());
    }
}
