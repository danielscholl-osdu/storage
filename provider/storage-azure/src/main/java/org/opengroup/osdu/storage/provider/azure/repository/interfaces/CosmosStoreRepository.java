package org.opengroup.osdu.storage.provider.azure.repository.interfaces;

import com.azure.cosmos.FeedOptions;
import com.azure.cosmos.SqlQuerySpec;
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

    List<T> findAllItems(String dataPartitionId, String cosmosDBName, String collection);

    List<T> queryItems(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec
            query, FeedOptions options);

    @Deprecated
    List<T> findAllItemsAsync(String dataPartitionId, String cosmosDBName, String collection, short pageSize,
                              int pageNum);

    @Deprecated
    List<T> queryItemsAsync(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query,
                            short pageSize, int pageNum);

    void upsertItem(String dataPartitionId, String cosmosDBName, String collection, T item);

    void createItem(String dataPartitionId, String cosmosDBName, String collection, T item);

    @Deprecated
    Page<T> findAllItemsAsyncPage(String dataPartitionId, String cosmosDBName, String collection, short pageSize,
                                  int pageNum);
    @Deprecated
    Page<T> queryItemsAsyncPage(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query,
                                short pageSize, int pageNum);

}
