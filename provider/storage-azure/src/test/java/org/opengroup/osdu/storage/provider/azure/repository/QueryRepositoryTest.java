package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.CosmosException;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryRepositoryTest {

    private static final String KIND1 = "ztenant:source:type:1.0.0";
    private static final String KIND2 = "atenant:source:type:1.0.0";
    private final String cosmosDBName = "osdu-db";
    private final String dataPartitionID = "opendes";
    private final String storageContainer = "StorageRecord";
    @Mock
    Page<RecordMetadataDoc> page;
    @Mock
    private RecordMetadataRepository recordMetadataRepository;
    @InjectMocks
    private QueryRepository queryRepository = new QueryRepository();
    @Mock(lenient = true)
    private CosmosStore cosmosStore;
    @Mock(lenient = true)
    private DpsHeaders dpsHeaders;
    @Mock
    private JaxRsDpsLog logger;

    @Mock(name = "CursorCache")
    private ICache<String, String> cursorCache;

    @BeforeEach
    public void setUp() {
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn(dataPartitionID);
        ReflectionTestUtils.setField(queryRepository, "record", recordMetadataRepository);
    }

    @Test
    void testGetAllKindsNoRecords() {
        // No records found
        List<String> result = new ArrayList<>();
        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result)); //th

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(null, null);

        assertEquals(datastoreQueryResult.getResults(), Collections.singletonList(result));
    }

    @Test
    public void testGetAllKindsOneRecord() {
        List<String> result = new ArrayList<>();
        result.add(KIND1);
        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result)); //th

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(null, null);

        // Expected one kind
        assertEquals(datastoreQueryResult.getResults().size(), result.size());
    }

    @Test
    public void testGetAllKindsMultipleRecord() {
        List<String> result = new ArrayList<>();
        result.add(KIND1);
        result.add(KIND2);

        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result));

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(null, null);

        List<String> results = datastoreQueryResult.getResults();
        assertEquals(results, Collections.singletonList(result));
    }

    @Test
    void getAllRecordIdsFromKindShouldThrowIllegalArgumentsException_whenKindIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> queryRepository.getAllRecordIdsFromKind(null, 10, "cursor", Optional.empty()));

        verify(recordMetadataRepository, never()).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());

        assertTrue(exception.getMessage().contains("kind must not be null"));
    }

    @Test
    void getAllRecordIdsFromKindShouldReturnAllRecords_whenKindIsNotNull() {
        //Arrange
        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), any(), eq(Optional.empty()))).thenReturn(page);
        RecordMetadataDoc doc1 = new RecordMetadataDoc("id1", createRecord("id1"));
        RecordMetadataDoc doc2 = new RecordMetadataDoc("id2", createRecord("id2"));
        when(page.getContent()).thenReturn(Arrays.asList(doc1, doc2));

        //Act
        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty());

        //Assertions
        verify(recordMetadataRepository).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());
        assertEquals(2, datastoreQueryResult.getResults().size());
        List<String> expectedResponse = Arrays.asList("id1", "id2");
        assertEquals(expectedResponse, datastoreQueryResult.getResults());
    }

    @Test
    void getAllRecordIdsFromKindShouldCallCosmosAgain_whenContinuationTokenIsPresent() {

        //
        Pageable pageable = CosmosStorePageRequest.of(0, 3, "continue");
        RecordMetadataDoc doc1 = new RecordMetadataDoc("id1", createRecord("id1"));
        RecordMetadataDoc doc2 = new RecordMetadataDoc("id2", createRecord("id2"));

        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), any(), eq(Optional.empty()))).thenReturn(page);
        when(page.getContent()).thenReturn(Arrays.asList(doc1, doc2));
        when(page.getPageable()).thenReturn(pageable);


        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty());


        verify(recordMetadataRepository, times(5)).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());
        assertEquals(10, datastoreQueryResult.getResults().size());
    }

    @Test
    void getAllRecordIdsByKindShouldThrowBadRequestException_whenCursorIsInvalid() {
        CosmosException cosmosException = mock(CosmosException.class);
        doReturn(HttpStatus.SC_BAD_REQUEST).when(cosmosException).getStatusCode();
        doReturn("INVALID JSON in continuation token").when(cosmosException).getMessage();

        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), any(), eq(Optional.empty()))).thenThrow(cosmosException);
        AppException exception = assertThrows(AppException.class, () -> queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty()));

        verify(recordMetadataRepository).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getError().getCode());
    }

    @Test
    void getAllRecordIdsByKindShouldRethrowException_whenCosmosExceptionIsEncounteredFromRecordRepository() {
        CosmosException cosmosException = mock(CosmosException.class);
        doReturn(HttpStatus.SC_BAD_REQUEST).when(cosmosException).getStatusCode();
        doReturn("Some other bad request").when(cosmosException).getMessage();

        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), any(), eq(Optional.empty()))).thenThrow(cosmosException);

        CosmosException exception = assertThrows(CosmosException.class, () -> queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty()));

        verify(recordMetadataRepository).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());
        assertEquals(HttpStatus.SC_BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getAllRecordIdsByKindShouldRethrowException_whenExceptionIsEncounteredFromRecordRepository() {
        Exception exception = new RuntimeException("some exception");
        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), any(), eq(Optional.empty()))).thenThrow(exception);

        Exception actualException = assertThrows(Exception.class, () -> queryRepository.getAllRecordIdsFromKind("kind", 10, "cursor", Optional.empty()));

        verify(recordMetadataRepository).findIdsByMetadata_kindAndMetadata_status(any(), any(), any(), any());
        assertEquals(exception, actualException);
    }

    @Test
    void getAllRecordIdsFromKindShouldReturnAllRecords_whenPaginationIsNotPresent() {
        List<RecordMetadataDoc> recordMetadataDocs = new ArrayList<>();
        recordMetadataDocs.add(new RecordMetadataDoc("id1", createRecord("id1")));
        recordMetadataDocs.add(new RecordMetadataDoc("id2", createRecord("id2")));
        List<String> expectedResponse = Arrays.asList("id1", "id2");

        when(recordMetadataRepository.findIdsByMetadata_kindAndMetadata_status(eq("kind"), eq("active"), eq(Optional.empty()))).thenReturn(recordMetadataDocs);

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllRecordIdsFromKind("kind", null, null, Optional.empty());

        verify(recordMetadataRepository).findIdsByMetadata_kindAndMetadata_status(any(), any(), any());
        assertEquals(2, datastoreQueryResult.getResults().size());
        assertEquals(expectedResponse, datastoreQueryResult.getResults());
    }

    @Test
    void getAllKindsShouldReturnAllKinds_whenRecordsArePresent() {
        List<Object> docs = new ArrayList<>();
        docs.add("Kind1");
        docs.add("Kind2");

        when(cosmosStore.queryItems(any(), any(), any(), any(), any(), any())).thenReturn(docs);
        when(cursorCache.get("cursor")).thenReturn("0");

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(1, "cursor");

        verify(cosmosStore).queryItems(any(), any(), any(), any(), any(), any());
        assertEquals(1, datastoreQueryResult.getResults().size());
    }

    @Test
    void getAllKindsShouldUseLimitAndIgnoreCursor_whenCursorIsNull() {
        List<Object> docs = new ArrayList<>();
        docs.add("Kind1");
        docs.add("Kind2");

        when(cosmosStore.queryItems(any(), any(), any(), any(), any(), any())).thenReturn(docs);

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(1, null);

        verify(cosmosStore).queryItems(any(), any(), any(), any(), any(), any());
        assertEquals(1, datastoreQueryResult.getResults().size());
    }

    @Test
    void getAllKindsShouldIgnoreCursorAndLimit_whenBothAreNullAndFetchAllKinds() {
        List<Object> docs = new ArrayList<>();
        docs.add("Kind1");
        docs.add("Kind2");

        when(cosmosStore.queryItems(any(), any(), any(), any(), any(), any())).thenReturn(docs);

        DatastoreQueryResult datastoreQueryResult = queryRepository.getAllKinds(null, null);

        verify(cosmosStore).queryItems(any(), any(), any(), any(), any(), any());
        assertEquals(2, datastoreQueryResult.getResults().size());
    }


    @Test
    void getAllKindsShouldRethrowException_whenCosmosExceptionIsThrown() {
        CosmosException expectedException = mock(CosmosException.class);
        when(cosmosStore.queryItems(any(), any(), any(), any(), any(), any())).thenThrow(expectedException);

        CosmosException exception = assertThrows(CosmosException.class, () -> queryRepository.getAllKinds(1, "cursor"));

        verify(cosmosStore, times(1)).queryItems(any(), any(), any(), any(), any(), any());
        assertEquals(expectedException, exception);
    }

    @Test
    void getAllKindsShouldRethrowException_whenGenericExceptionIsThrown() {
        RuntimeException expectedException = mock(RuntimeException.class);
        when(cosmosStore.queryItems(any(), any(), any(), any(), any(), any())).thenThrow(expectedException);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> queryRepository.getAllKinds(1, "cursor"));

        verify(cosmosStore, times(1)).queryItems(any(), any(), any(), any(), any(), any());
        assertEquals(expectedException, exception);
    }

    private RecordMetadata createRecord(String recordId) {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(recordId);
        Acl recordAcl = new Acl();
        String[] owners = {"owner1@devint.osdu.com"};
        String[] viewers = {"viewer1@devint.osdu.com"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);
        recordMetadata.setModifyUser("user1");
        recordMetadata.setModifyTime(123L);
        return recordMetadata;
    }

}
