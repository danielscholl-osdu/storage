package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleCosmosStoreRepositoryTest {
    private static final String ORDER_ASC_BY_ID = "ORDER BY c.field ASC";
    private static String DATA_PARTITION_ID = "opendes";
    private static String COSMOS_DB_NAME = "osdu-db";
    private static String COLLECTION = "collection";
    @Mock
    private DpsHeaders headers;
    @Mock
    private CosmosStore cosmosStore;
    @InjectMocks
    private SimpleCosmosStoreRepository simpleCosmosStoreRepository;

    @BeforeEach
    void setup() {
        lenient().when(headers.getPartitionId()).thenReturn("opendes");
        ReflectionTestUtils.setField(simpleCosmosStoreRepository, "operation", cosmosStore);
    }

    @Test
    void findAll_shouldCallCosmosStoreAndReturnAllRecords() {
        simpleCosmosStoreRepository.findAll(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION);

        verify(cosmosStore).findAllItems(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any());
    }

    @Test
    void findAllWithPageable_shouldCallCosmosStoreAndReturnAllRecords() {
        Pageable pageable = PageRequest.of(0, 2, Sort.by("field"));

        simpleCosmosStoreRepository.findAll(pageable, DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION);

        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItemsPage(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), argumentCaptor.capture(), any(), eq(2), any(), any(CosmosQueryRequestOptions.class));
        assertTrue(argumentCaptor.getValue().getQueryText().contains(ORDER_ASC_BY_ID));
    }

    @Test
    void findAllWithSort_shouldCallCosmosStoreAndReturnAllRecords() {
        simpleCosmosStoreRepository.findAll(Sort.by("field"), DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION);

        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        verify(cosmosStore).queryItems(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), argumentCaptor.capture(), any(CosmosQueryRequestOptions.class), any());

        assertTrue(argumentCaptor.getValue().getQueryText().contains(ORDER_ASC_BY_ID));
    }

    @Test
    void findByIdShouldThrowIllegalArgumentsExceptionAndNotCallCosmosStore_whenIdIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> simpleCosmosStoreRepository.findById(null, DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, null));

        verify(cosmosStore, never()).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), any());

        assertTrue(exception.getMessage().contains("id must not be null"));
    }

    @Test
    void findByIdShouldReturnEmptyAndNotCallCosmosStore_whenIdIsBlank() {
        ReflectionTestUtils.setField(simpleCosmosStoreRepository, "domainClass", String.class);
        Optional<String> ret = simpleCosmosStoreRepository.findById("", DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "");

        verify(cosmosStore, never()).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), any());

        assertTrue(ret.isEmpty());
    }

    @Test
    void findByIdShouldCallCosmosStoreAndReturnRecord_whenValidIdIsPassed() {
        ReflectionTestUtils.setField(simpleCosmosStoreRepository, "domainClass", String.class);
        when(cosmosStore.findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq("id"), eq("id"), eq(String.class))).thenReturn(Optional.of("record"));

        Optional<String> ret = simpleCosmosStoreRepository.findById("id", DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "id");

        verify(cosmosStore).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq("id"), eq("id"), eq(String.class));

        assertTrue(ret.isPresent());
        assertEquals("record", ret.get());
    }


    @Test
    void existsShouldThrowIllegalArgumentsExceptionAndNotCallCosmosStore_whenIdIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> simpleCosmosStoreRepository.exists(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, null, null));

        verify(cosmosStore, never()).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), any());

        assertTrue(exception.getMessage().contains("id must not be null"));
    }

    @Test
    void existsShouldCallCosmosStoreAndReturnTrue_whenValidIdIsPassedAndRecordIsPresent() {
        when(cosmosStore.findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq("id"), eq("id"), any())).thenReturn(Optional.of("record"));

        Boolean ret = simpleCosmosStoreRepository.exists(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "id", "id");

        verify(cosmosStore).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), any());
        assertTrue(ret);
    }

    @Test
    void existsByIdShouldThrowIllegalArgumentsExceptionAndNotCallCosmosStore_whenPrimaryKeyIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> simpleCosmosStoreRepository.existsById(null, DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, null));

        verify(cosmosStore, never()).findItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), any());
        assertTrue(exception.getMessage().contains("primaryKey should not be null"));
    }

    @Test
    void existsByIdShouldCallCosmosStoreAndReturnTrue_whenValidIdIsPassedAndRecordIsPresent() {
        when(cosmosStore.findItem(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("record"));

        Boolean ret = simpleCosmosStoreRepository.existsById("primary_key", DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "partition_key");

        verify(cosmosStore).findItem(any(), any(), any(), any(), any(), any());
        assertTrue(ret);
    }

    @Test
    void paginationQueryThrowsIllegalArgumentsException_whenPageableSizeIsLessThan0() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> simpleCosmosStoreRepository.paginationQuery(Mockito.mock(Pageable.class), Mockito.mock(SqlQuerySpec.class), String.class, "", "", "", Mockito.mock(CosmosQueryRequestOptions.class)));

        verify(cosmosStore, never()).queryItemsPage(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(SqlQuerySpec.class), any(), any(int.class), any(), any(CosmosQueryRequestOptions.class));

        assertTrue(exception.getMessage().contains("pageable should have page size larger than 0"));
    }

    @Test
    void findWithPageable_shouldCallCosmosStoreAndReturnAllRecords() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));

        simpleCosmosStoreRepository.find(pageable, DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, new SqlQuerySpec("queryText"), new CosmosQueryRequestOptions());

        verify(cosmosStore).queryItemsPage(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), any(), any(), eq(10), any(), any());
    }

    @Test
    void createItem_shouldCallCosmosStoreAndCreateRecordIfItemIsNotNull() {
        simpleCosmosStoreRepository.createItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "partitionKey", "New Item");

        verify(cosmosStore).createItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq("partitionKey"), eq("New Item"));
    }

    @Test
    void createItem_shouldThrowIllegalArgumentsExceptionsIfItemIsNull() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> simpleCosmosStoreRepository.createItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, "partitionKey", null));

        verify(cosmosStore, never()).createItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq("partitionKey"), eq("New Item"));

        assertTrue(exception.getMessage().contains("entity must not be null"));
    }

    @Test
    void queryItemsPage_shouldCallCosmosStore() {
        SqlQuerySpec querySpec = new SqlQuerySpec("query");
        simpleCosmosStoreRepository.queryItemsPage(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, querySpec, String.class, 10, "continuationToken");

        verify(cosmosStore).queryItemsPage(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq(querySpec), eq(String.class), eq(10), eq("continuationToken"));
    }

}
