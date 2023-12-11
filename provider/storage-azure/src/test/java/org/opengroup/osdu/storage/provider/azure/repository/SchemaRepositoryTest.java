package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.azure.SchemaDoc;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaRepositoryTest {

    private final Schema schema = mock(Schema.class);
    private final String DATA_PARTITION_ID = "opendes";
    private final String COSMOS_DB_NAME = "osdu-db";
    private final String COLLECTION = "collection";
    private final String KIND = "opendes:wks:work-product-component--wellLog:1.0.0";
    @Mock
    CosmosStore cosmosStore;
    @InjectMocks
    SchemaRepository schemaRepository;
    @Mock
    private DpsHeaders dpsHeaders;
    @Mock
    private CosmosContainerConfig cosmosContainerConfig;

    @BeforeEach
    void setup() {
        lenient().when(dpsHeaders.getPartitionId()).thenReturn(DATA_PARTITION_ID);
        lenient().when(schema.getKind()).thenReturn(KIND);
        ReflectionTestUtils.setField(schemaRepository, "operation", cosmosStore);
        ReflectionTestUtils.setField(schemaRepository, "cosmosDBName", COSMOS_DB_NAME);
        ReflectionTestUtils.setField(schemaRepository, "schemaCollection", COLLECTION);
    }

    @Test
    void AddShouldThrowIllegalArgumentException_whenSchemaIsNull() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> schemaRepository.add(null, "user"));

        Assertions.assertTrue(exception.getMessage().contains("schema must not be null"));
    }


    @Test
    void AddShouldThrowIllegalArgumentException_whenUserIsNull() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> schemaRepository.add(mock(Schema.class), null));

        Assertions.assertTrue(exception.getMessage().contains("user must not be null"));
    }


    @Test
    void AddShouldThrowIllegalArgumentException_whenKindAlreadyExists() {
        Optional<SchemaDoc> schemaDoc = Optional.of(mock(SchemaDoc.class));
        when(cosmosStore.findItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, KIND, KIND, SchemaDoc.class)).thenReturn(schemaDoc);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> schemaRepository.add(schema, "user"));

        Assertions.assertTrue(exception.getMessage().contains(String.format("Schema %s already exists. Can't create again.", KIND)));
    }

    @Test
    void AddShouldUpsertSchemaInCosmos_whenNoneExists() {
        when(cosmosStore.findItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, KIND, KIND, SchemaDoc.class)).thenReturn(Optional.empty());

        schemaRepository.add(schema, "user");

        verify(cosmosStore).upsertItem(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), eq(KIND), any());
    }

    @Test
    void GetShouldReturnNull_whenNoRecordsAreReturnedFromCosmos() {
        when(cosmosStore.findItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, KIND, KIND, SchemaDoc.class)).thenReturn(Optional.empty());

        Schema foundItem = schemaRepository.get(KIND);

        Assertions.assertNull(foundItem);
    }

    @Test
    void GetShouldReturnSchema_whenRecordsAreReturnedFromCosmos() {
        when(cosmosStore.findItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, KIND, KIND, SchemaDoc.class)).thenReturn(Optional.of(mock(SchemaDoc.class)));

        Schema foundItem = schemaRepository.get(KIND);

        Assertions.assertNotNull(foundItem);
    }

    @Test
    void mapReturnsSchema_whenSchemaDocIsProvided() {
        Map<String, Object> extension = new HashMap<>();
        SchemaItem[] items = new SchemaItem[2];
        SchemaDoc schemaDoc = new SchemaDoc("kind", "id", extension, "user", items);

        Schema schemaReturned = schemaRepository.map(schemaDoc);

        Assertions.assertEquals(schemaReturned.getKind(), schemaDoc.getKind());
        Assertions.assertEquals(schemaReturned.getSchema(), schemaDoc.getSchemaItems());
        Assertions.assertEquals(schemaReturned.getExt(), schemaDoc.getExtension());
    }

    @Test
    void deleteShouldDeleteFromCosmos_whenIdIsNotNull() {
        schemaRepository.delete(KIND);

        verify(cosmosStore).deleteItem(DATA_PARTITION_ID, COSMOS_DB_NAME, COLLECTION, KIND, KIND);
    }

    @Test
    void findAllWithPageableShouldFetchRecords_whenPageableIsPresent() {
        schemaRepository.findAll(Pageable.ofSize(1));

        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);

        verify(cosmosStore).queryItemsPage(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), argumentCaptor.capture(), eq(SchemaDoc.class), eq(1), any(), any(CosmosQueryRequestOptions.class));

        Assertions.assertEquals("SELECT * FROM c ", argumentCaptor.getValue().getQueryText());
    }

    @Test
    void findAllWithSortShouldFetchRecords_whenSortIsPresent() {
        schemaRepository.findAll(Sort.by("id"));
        ArgumentCaptor<SqlQuerySpec> argumentCaptor = ArgumentCaptor.forClass(SqlQuerySpec.class);
        String expectedQuery = "SELECT * FROM c ORDER BY c.id ASC";

        verify(cosmosStore).queryItems(eq(DATA_PARTITION_ID), eq(COSMOS_DB_NAME), eq(COLLECTION), argumentCaptor.capture(), any(CosmosQueryRequestOptions.class), eq(SchemaDoc.class));

        Assertions.assertEquals(expectedQuery, argumentCaptor.getValue().getQueryText());
    }
}
