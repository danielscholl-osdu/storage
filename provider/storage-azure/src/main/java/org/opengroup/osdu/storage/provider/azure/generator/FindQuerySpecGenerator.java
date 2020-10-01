package org.opengroup.osdu.storage.provider.azure.generator;

import com.azure.cosmos.SqlQuerySpec;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStoreQuery;
import org.springframework.lang.NonNull;

public class FindQuerySpecGenerator extends AbstractQueryGenerator  {

    public SqlQuerySpec generate(@NonNull CosmosStoreQuery query) {
        return super.generateQuery(query, "SELECT * FROM c");
    }

    public SqlQuerySpec generateWithQueryText(@NonNull CosmosStoreQuery query, String queryText) {
        return super.generateQuery(query, queryText);
    }

    public SqlQuerySpec generateCosmos(CosmosStoreQuery query) {
        return super.generateCosmosQuery(query, "SELECT * FROM c");
    }

    public SqlQuerySpec generateCosmosWithQueryText(CosmosStoreQuery query, String queryText) {
        return super.generateCosmosQuery(query, queryText);
    }

    public FindQuerySpecGenerator() {
    }
}
