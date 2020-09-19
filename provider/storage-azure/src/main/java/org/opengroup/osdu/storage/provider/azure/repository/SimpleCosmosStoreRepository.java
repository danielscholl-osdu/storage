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
import com.microsoft.azure.spring.data.cosmosdb.core.query.Criteria;
import com.microsoft.azure.spring.data.cosmosdb.core.query.CriteriaType;
import com.microsoft.azure.spring.data.cosmosdb.core.query.DocumentQuery;
import org.opengroup.osdu.storage.provider.azure.repository.interfaces.CosmosStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;


public class SimpleCosmosStoreRepository<T> implements CosmosStoreRepository<T> {

    private static final String ID_MUST_NOT_BE_NULL = "id must not be null";
    private static final String ENTITY_MUST_NOT_BE_NULL = "entity must not be null";
    private static final String PAGEABLE_MUST_NOT_BE_NULL = "pageable must not be null";

    private final Class<T> domainClass;

    @Autowired
    private AdvancedCosmosStore operation;

    public SimpleCosmosStoreRepository(Class<T> domainClass) {
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
        return this.operation.findAllItemsAsyncPage(dataPartitionId, cosmosDBName, collection, this.getDomainClass(), pageSize, pageNum);
    }

    public List<T> queryItemsAsync(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, short pageSize, int pageNum) {
        return this.operation.queryItemsAsync(dataPartitionId, cosmosDBName, collection, query, this.getDomainClass(), pageSize, pageNum);
    }

    public Page<T> queryItemsAsyncPage(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, short pageSize, int pageNum) {
        return this.operation.queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, query, this.getDomainClass(), pageSize, pageNum);
    }

    public void upsertItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull T item) {
        Assert.notNull(item, ENTITY_MUST_NOT_BE_NULL);
        this.operation.upsertItem(dataPartitionId, cosmosDBName, collection, item);
    }

    @Override
    public void createItem(String dataPartitionId, String cosmosDBName, String collection, T item) {
        Assert.notNull(item, ENTITY_MUST_NOT_BE_NULL);
        this.operation.createItem(dataPartitionId, cosmosDBName, collection, item);
    }

    // End CosmosStore methods

    // Standard Spring Data Repository
    /*
    @Override
    public Optional<T> findById(String id, String partitionKey) {
        return Optional.empty();
    }

    @Override
    public void deleteById(String id, String partitionKey) {
    }
    @Override
    public Iterable<T> findAll(Sort var1) {
        return null;
    }

    @Override
    public Page<T> findAll(Pageable var1) {
        return null;
    }

    @Override
    public <T> T save(T entity) {
        return null;
    }

    @Override
    public <T> Iterable<T> saveAll(Iterable<T> entities) {
        return null;
    }

    @Override
    public Optional<T> findById(String id) {
        return Optional.empty();
    }
    7
    @Override
    public boolean existsById(String id) {
        return false;
    }

    @Override
    public Iterable<T> findAll() {
        return null;
    }

    @Override
    public Iterable<T> findAllById(Iterable<String> ids) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(String id) {
    }
    */

    // Start Spring data repository like methods

    @Override
    public T save(T entity, String dataPartitionId, String cosmosDBName, String collection) {
        Assert.notNull(entity, ENTITY_MUST_NOT_BE_NULL);
        this.upsertItem(dataPartitionId, cosmosDBName, collection, entity);
        return entity;
    }

    @Override
    public Iterable<T> saveAll(@NonNull Iterable<T> entities, String dataPartitionId, String cosmosDBName, String collection) {
        Assert.notNull(entities, "Iterable entities should not be null");
        List<T> savedEntities = new ArrayList();
        entities.forEach((entity) -> {
            T savedEntity = this.save(entity, dataPartitionId, cosmosDBName, collection);
            savedEntities.add(savedEntity);
        });
        return savedEntities;
    }

    @Override
    public Iterable<T> findAll(String dataPartitionId, String cosmosDBName, String collection) {
        return this.findAllItems(dataPartitionId, cosmosDBName, collection);
    }

    @Override
    public Optional<T> findById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, "id must not be null");
        return !StringUtils.hasText(id) ? Optional.empty() : this.findItem(dataPartitionId,  cosmosDBName,  collection,  id,  partitionKey);
    }

    @Override
    public void deleteById(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        this.deleteItem(dataPartitionId, cosmosDBName, collection, id, partitionKey);
    }

    @Override
    public boolean existsById(@NonNull String primaryKey, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(primaryKey, "primaryKey should not be null");
        return this.exists(primaryKey, dataPartitionId, cosmosDBName, collection, partitionKey);
    }

    @Override
    public Iterable<T> findAll(@NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection) {
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
        FeedOptions options = new FeedOptions().setEnableCrossPartitionQuery(true);
        return this.queryItems(dataPartitionId, cosmosDBName, collection, query, options);
    }

    @Override
    public List<T> findAllById(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(ids, "Iterable ids should not be null");
        return this.findByIds(ids, dataPartitionId,  cosmosDBName,  collection,  partitionKey);
    }

    @Override
    public List<T> findByIds(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(ids, "Id list should not be null");
        DocumentQuery documentQuery = new DocumentQuery(Criteria.getInstance(CriteriaType.IN, "id", Collections.singletonList(ids)));
        SqlQuerySpec query = new SqlQuerySpec(documentQuery.toString());
        return this.find(dataPartitionId, cosmosDBName,  collection, query);
    }

    @Override
    public Page<T> findAll(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection) {
        Assert.notNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
        //else {
        //    if (isUnpaged(pageable)) {
        //        return new PageImpl<T>((List<T>) this.findAll(dataPartitionId, cosmosDBName, collection));
        //    }
        //}
        int pageNum = pageable.getPageNumber();
        short pageSize = (short) pageable.getPageSize();
        System.out.println("pageNum pageSize=" + pageNum + " " + pageSize);
        return this.findAllItemsAsyncPage(dataPartitionId, cosmosDBName, collection, pageSize, pageNum);
    }

    @Override
    public Page<T> findAll(@NonNull Pageable pageable, @NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection) {
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

    @Override
    public Page<T> find(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query) {
        Assert.notNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
        //else {
        //    if (isUnpaged(pageable)) {
        //        return new PageImpl<T>((List<T>) this.findAll(dataPartitionId, cosmosDBName, collection));
        //    }
        //}
        int pageNum = pageable.getPageNumber();
        short pageSize = (short) pageable.getPageSize();
        return this.queryItemsAsyncPage(dataPartitionId, cosmosDBName, collection, query, pageSize, pageNum);
    }

    @Override
    public List<T> find(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query) {
        FeedOptions options = new FeedOptions().setEnableCrossPartitionQuery(true);
        return this.queryItems(dataPartitionId, cosmosDBName, collection, query, options);
    }

    @Override
    public synchronized T getOne(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        Optional<T> doc = this.findItem(dataPartitionId, cosmosDBName, collection, id, partitionKey);
        if (!doc.isPresent())
            return null;
        return doc.get();
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


}
