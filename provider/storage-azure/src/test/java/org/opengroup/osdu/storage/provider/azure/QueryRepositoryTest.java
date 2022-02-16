package org.opengroup.osdu.storage.provider.azure;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.azure.repository.QueryRepository;
import org.opengroup.osdu.storage.provider.azure.repository.SchemaRepository;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class QueryRepositoryTest {

    private final String cosmosDBName = "osdu-db";
    private final String dataPartitionID = "opendes";
    private final String storageContainer = "StorageRecord";

    @InjectMocks
    private QueryRepository repo = new QueryRepository();

    @Mock(lenient = true)
    private CosmosStore cosmosStore;

    @Mock(lenient = true)
    private DpsHeaders dpsHeaders;

    @Before
    public void setUp() {
        initMocks(this);
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn(dataPartitionID);
    }

    private static final String KIND1 = "ztenant:source:type:1.0.0";
    private static final String KIND2 = "atenant:source:type:1.0.0";


    @Test
    public void testGetAllKindsNoRecords() {
        // No records found
        List<String> result = new ArrayList<>();
        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result)); //th
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        Assert.assertEquals(datastoreQueryResult.getResults(), Collections.singletonList(result));
    }

    @Test
    public void testGetAllKindsOneRecord() {
        List<String> result = new ArrayList<>();
        result.add(KIND1);
        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result)); //th
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        // Expected one kind
        Assert.assertEquals(datastoreQueryResult.getResults().size(), result.size());
    }

    @Test
    public void testGetAllKindsMultipleRecord() {
        List<String> result = new ArrayList<>();
        result.add(KIND1);
        result.add(KIND2);
        Mockito.when(cosmosStore.queryItems(eq(dataPartitionID), eq(cosmosDBName), eq(storageContainer), any(), any(), any())).thenReturn(Collections.singletonList(result)); //th
        DatastoreQueryResult datastoreQueryResult = repo.getAllKinds(null, null);
        List<String> results = datastoreQueryResult.getResults();
        Assert.assertEquals(results, Collections.singletonList(result));
    }
}
