//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.FeedOptions;
import com.azure.cosmos.SqlQuerySpec;
import org.opengroup.osdu.azure.CosmosStore;
import org.opengroup.osdu.storage.provider.azure.repository.interfaces.IRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;


public class CosmosStoreRepository<T> implements IRepository<T> {

    private static final String ID_MUST_NOT_BE_NULL = "id must not be null";
    private static final String ENTITY_MUST_NOT_BE_NULL = "entity must not be null";
    private static final String PAGEABLE_MUST_NOT_BE_NULL = "pageable must not be null";
    private static final Boolean requirePagination = false;

    private final Class<T> domainClass;

    @Autowired
    private ExtendedCosmosStore operation;

    public CosmosStoreRepository(Class<T> domainClass) {
         this.domainClass = domainClass;
    }

    public Class<T> getDomainClass() {
        return this.domainClass;
    }

    // Start CosmosStore methods

    public void deleteItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String id, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        this.operation.deleteItem(dataPartitionId, cosmosDBName, collection, id, partitionKey);
    }

    public Optional<T> findItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String id, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        return this.operation.findItem(dataPartitionId, cosmosDBName, collection, id, partitionKey, this.getDomainClass());
    }

    public boolean exists(String dataPartitionId, String cosmosDBName, String collection, @NonNull String id, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        return this.operation.findItem(dataPartitionId, cosmosDBName, collection, id, partitionKey, this.getDomainClass()).isPresent();
    }

    public List<T> findAllItems(String dataPartitionId, String cosmosDBName, String collection) {
        return this.operation.findAllItems(dataPartitionId, cosmosDBName, collection, this.getDomainClass());
    }

    public List<T> queryItems(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, FeedOptions options) {
        return this.operation.queryItems(dataPartitionId, cosmosDBName, collection, query, options, this.getDomainClass());
    }

    public List<T> findAllItemsAsync(String dataPartitionId, String cosmosDBName, String collection, short pageSize, int pageNum) {
        return  this.operation.findAllItemsAsync(dataPartitionId, cosmosDBName, collection, this.getDomainClass(), pageSize, pageNum);
    }

    public Page<T> findAllItemsAsyncPage(String dataPartitionId, String cosmosDBName, String collection, short pageSize, int pageNum) {
        return  this.operation.findAllItemsAsyncPage(dataPartitionId, cosmosDBName, collection, this.getDomainClass(), pageSize, pageNum);
    }

    public List<T> queryItemsAsync(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, short pageSize, int pageNum) {
        return  this.operation.queryItemsAsync(dataPartitionId, cosmosDBName, collection, query, this.getDomainClass(), pageSize, pageNum);
    }

    public Page<T> queryItemsAsyncPage(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, short pageSize, int pageNum) {
        return  this.operation.queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, query, this.getDomainClass(), pageSize, pageNum);
    }

    public void upsertItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull T item) {
        Assert.notNull(item, ENTITY_MUST_NOT_BE_NULL);
        this.operation.upsertItem(dataPartitionId, cosmosDBName, collection, item);
    }

    // End CosmosStore methods

    // Start Spring data repository like methods

    public synchronized T getOne(String dataPartitionId, String cosmosDBName, String collection, @NonNull String id, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        Optional<T> doc = this.findItem(dataPartitionId, cosmosDBName, collection, id, partitionKey);
        if (!doc.isPresent())
            return null;
        return doc.get();
    }

    public T save(String dataPartitionId, String cosmosDBName, String collection, T entity) {
        Assert.notNull(entity, ENTITY_MUST_NOT_BE_NULL);
        this.upsertItem(dataPartitionId, cosmosDBName, collection, entity);
        return entity;
    }

    public Iterable<T> saveAll(@NonNull Iterable<T> entities, String dataPartitionId, String cosmosDBName, String collection) {
        Assert.notNull(entities, "Iterable entities should not be null");
        List<T> savedEntities = new ArrayList();
        entities.forEach((entity) -> {
            T savedEntity = this.save(dataPartitionId, cosmosDBName, collection, entity);
            savedEntities.add(savedEntity);
        });
        return savedEntities;
    }

    public Iterable<T> findAll(String dataPartitionId, String cosmosDBName, String collection) {
        return this.findAllItems(dataPartitionId, cosmosDBName, collection);
    }

    public Optional<T> findById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, "id must not be null");
        return !StringUtils.hasText(id) ? Optional.empty() : this.findItem(dataPartitionId,  cosmosDBName,  collection,  id,  partitionKey);
    }

    public void deleteById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        this.deleteItem(dataPartitionId, cosmosDBName, id, collection, partitionKey);
    }

    public boolean existsById(@NonNull String primaryKey, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(primaryKey, "primaryKey should not be null");
        return this.exists(primaryKey, dataPartitionId, cosmosDBName, collection, partitionKey);
    }

    public Iterable<T> findAll(@NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection, FeedOptions options) {
        Assert.notNull(sort, "sort of findAll should not be null");
        SqlQuerySpec query = new SqlQuerySpec();
        StringBuilder sb = new StringBuilder("SELECT * FROM c");
        List<Sort.Order> orders = toOrders(sort);
        Assert.notNull(orders, "sort orders of findAll should not be null");
        if (orders != null && orders.size() > 0) {
            sb.append(" ORDER BY ");
            int i = 0;
            for (Sort.Order order : orders) {
                if (i++ > 0) sb.append(",");
                sb.append(" c." + order.getProperty() + " " + order.getDirection());
            }
        }
        query.setQueryText(sb.toString());
        return this.queryItems(dataPartitionId, cosmosDBName, collection, query, options);
    }

    public Page<T> findAll(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection) {
        if (requirePagination) {
            paginationRequired(pageable);
        }
        //else {
        //    if (isUnpaged(pageable)) {
        //        return new PageImpl<T>((List<T>) this.findAll(dataPartitionId, cosmosDBName, collection));
        //    }
        //}
        int pageNum = pageable.getPageNumber();
        short pageSize = (short) pageable.getPageSize();
        return this.findAllItemsAsyncPage(dataPartitionId, cosmosDBName, collection, pageSize, pageNum);
    }

    public Page<T> findAll(@NonNull Pageable pageable, @NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection, FeedOptions options) {
        Assert.notNull(sort, "sort of findAll should not be null");
        SqlQuerySpec query = new SqlQuerySpec();
        StringBuilder sb = new StringBuilder("SELECT * FROM c");
        List<Sort.Order> orders = toOrders(sort);
        Assert.notNull(orders, "sort orders of findAll should not be null");
        if (orders != null && orders.size() > 0) {
            sb.append(" ORDER BY ");
            int i = 0;
            for (Sort.Order order : orders) {
                if (i++ > 0) sb.append(",");
                sb.append(" c." + order.getProperty() + " " + order.getDirection());
            }
        }
        query.setQueryText(sb.toString());
        int pageNum = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        return this.queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, query, (short) pageSize, pageNum);
    }

    public Page<T> query(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query) {
        if (requirePagination) {
            paginationRequired(pageable);
        }
        //else {
        //    if (isUnpaged(pageable)) {
        //        return new PageImpl<T>((List<T>) this.findAll(dataPartitionId, cosmosDBName, collection));
        //    }
        //}
        int pageNum = pageable.getPageNumber();
        short pageSize = (short) pageable.getPageSize();
        return this.queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, query, pageSize, pageNum);
    }

    private static List<Sort.Order> toOrders(Sort sort) {
        if (sort.isUnsorted()) {
            return Collections.emptyList();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            orders.add(order);
        }
        return orders;
    }

    private static boolean isUnpaged(Pageable pageable) {
        return pageable.isUnpaged();
    }

    private static void paginationRequired(Pageable pageable) {
        Assert.notNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
    }

    // End Spring data repository like methods

    // Start OSDU specific methods

    // End OSDU specific methods
}
