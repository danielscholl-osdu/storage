// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.generator;

import com.azure.cosmos.models.SqlQuerySpec;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStoreQuery;
import org.springframework.lang.NonNull;

public class FindQuerySpecGenerator extends AbstractQueryGenerator {

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
