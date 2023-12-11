package org.opengroup.osdu.storage.provider.azure.generator;

import com.azure.cosmos.models.SqlQuerySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStoreQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FindQuerySpecGeneratorTest {

    @InjectMocks
    FindQuerySpecGenerator findQuerySpecGenerator;

    @Test
    void generateWithQueryTextShouldCreateQueryWithOrdering_whenOrderingIsPresent() {
        CosmosStoreQuery query = new CosmosStoreQuery();
        query.with(mock(Pageable.class));
        query.with(Sort.by("id")).with(Sort.by("field2"));

        String queryText = "Select id, f1, f2, f3 from c";
        String expectedQuery = "Select id, f1, f2, f3 from c ORDER BY c.field2 ASC,c.id ASC";

        SqlQuerySpec querySpec = findQuerySpecGenerator.generateWithQueryText(query, queryText);

        assertEquals(expectedQuery, querySpec.getQueryText());
    }

    @Test
    void generateWithQueryTextShouldCreateQueryWithoutOrdering_whenOrderingIsNotPresent() {
        CosmosStoreQuery query = mock(CosmosStoreQuery.class);
        Mockito.when(query.getSort()).thenReturn(Sort.unsorted());

        String queryText = "Select id, f1, f2, f3";
        String expectedQuery = "Select id, f1, f2, f3 ";

        SqlQuerySpec querySpec = findQuerySpecGenerator.generateWithQueryText(query, queryText);

        assertEquals(expectedQuery, querySpec.getQueryText());
    }

    @Test
    void generateWithQueryTextShouldThrowIllegalArgumentsException_whenIgnoreCaseIsPresentOnSort() {
        //arrange
        CosmosStoreQuery query = mock(CosmosStoreQuery.class);
        Mockito.when(query.getSort()).thenReturn(Sort.by(Sort.Order.by("id").ignoreCase()));

        //act
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> findQuerySpecGenerator.generateWithQueryText(query, "queryText"));

        assertTrue(exception.getMessage().contains("Ignore case is not supported"));
    }

    @Test
    void generateShouldGenerateQuery_whenCosmosQueryIsProvided() {
        CosmosStoreQuery query = mock(CosmosStoreQuery.class);
        Mockito.when(query.getSort()).thenReturn(Sort.by("id"));
        String expectedQuery = "SELECT * FROM c ORDER BY c.id ASC";

        SqlQuerySpec sqlQuerySpec = findQuerySpecGenerator.generate(query);

        assertEquals(expectedQuery, sqlQuerySpec.getQueryText());
    }

    @Test
    void generateWithQueryTextShouldIgnoreOrder_whenOrderingIsNotPresent() {
        CosmosStoreQuery query = new CosmosStoreQuery();
        query.with(mock(Pageable.class));
        query.with(Sort.by("id")).with(Sort.unsorted());

        String queryText = "Select id, f1, f2, f3 from c";
        String expectedQuery = "Select id, f1, f2, f3 from c ORDER BY c.id ASC";

        SqlQuerySpec querySpec = findQuerySpecGenerator.generateWithQueryText(query, queryText);
        assertEquals(expectedQuery, querySpec.getQueryText());
    }
}
