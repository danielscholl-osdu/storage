package org.opengroup.osdu.storage.provider.azure.repository.interfaces;

import com.azure.cosmos.FeedOptions;
import com.azure.cosmos.SqlQuerySpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface IRepository<T> {

    // CosmosStore Methods

    public void deleteItem(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    public Optional<T> findItem(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    public boolean exists(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    public List<T> findAllItems(String dataPartitionId, String cosmosDBName, String collection);

    public List<T> queryItems(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, FeedOptions options);

    public List<T> findAllItemsAsync(String dataPartitionId, String cosmosDBName, String collection, short pageSize, int pageNum);

    public List<T> queryItemsAsync(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, short pageSize, int pageNum);

    public void upsertItem(String dataPartitionId, String cosmosDBName, String collection, T item);

    // Spring-like Standard Repository Methods

    public T getOne(String dataPartitionId, String cosmosDBName, String collection, String id, String partitionKey);

    public T save(String dataPartitionId, String cosmosDBName, String collection, T entity);

    public Iterable<T> saveAll(Iterable<T> entities, String dataPartitionId, String cosmosDBName, String collection);

    public Iterable<T> findAll(String dataPartitionId, String cosmosDBName, String collection);

    public Optional<T> findById(String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    public void deleteById(String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    public boolean existsById(String primaryKey, String dataPartitionId, String cosmosDBName, String collection, String partitionKey);

    public Iterable<T> findAll(@NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection, FeedOptions options);

    public Page<T> findAll(Pageable pageable, String dataPartitionId, String cosmosDBName, String collection);

    public Page<T> query(Pageable pageable, String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query);

}