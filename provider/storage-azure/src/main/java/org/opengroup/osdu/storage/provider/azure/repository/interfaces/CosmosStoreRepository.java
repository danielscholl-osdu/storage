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

package org.opengroup.osdu.storage.provider.azure.repository.interfaces;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;

public interface CosmosStoreRepository<T> extends PagingAndSortingRepository<T> {

    // Standard Spring Data Repository
    /*
       Optional<T> findById(String id, String partitionKey);
        void deleteById(String id,  String partitionKey);
    */

    Optional<T> findById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    void deleteById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    // Internal Cosmos Store methods

    void deleteItem(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    Optional<T> findItem(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    boolean exists(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    boolean existsById(@NonNull String primaryKey, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    public List<T> find(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query);

    List<T> findAllItems(String dataPartitionId, String cosmosDBName, String collection);

    List<T> queryItems(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec
            query, CosmosQueryRequestOptions options);

    void upsertItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String partitionKey, @NonNull T item);

    void createItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String partitionKey, T item);

    <T> Page<T> queryItemsPage(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, Class<T> clazz,
                               int pageSize, String continuationToken);


}
