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


import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.microsoft.azure.documentdb.bulkexecutor.BulkImportResponse;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.cosmosdb.CosmosStoreBulkOperations;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.azure.generator.FindQuerySpecGenerator;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStoreQuery;
import org.opengroup.osdu.storage.provider.azure.repository.interfaces.CosmosStoreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public class SimpleCosmosStoreRepository<T> implements CosmosStoreRepository<T> {

    private static final String ID_MUST_NOT_BE_NULL = "id must not be null";
    private static final String ENTITY_MUST_NOT_BE_NULL = "entity must not be null";
    private static final String PAGEABLE_MUST_NOT_BE_NULL = "pageable must not be null";

    private final Class<T> domainClass;

    @Autowired
    private CosmosStore operation;

    @Autowired
    private CosmosStoreBulkOperations cosmosBulkStore;

    @Autowired
    private int bulkImportMaxConcurrencyPePartitionRange;

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

    public List<T> queryItems(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, CosmosQueryRequestOptions options) {
        return this.operation.queryItems(dataPartitionId, cosmosDBName, collection, query, options, this.getDomainClass());
    }

    @Override
    public void upsertItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String partitionKey, @NonNull T item) {
        Assert.notNull(item, ENTITY_MUST_NOT_BE_NULL);
        this.operation.upsertItem(dataPartitionId, cosmosDBName, collection, partitionKey, item);
    }

    @Override
    public <T> Page<T> queryItemsPage(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query, Class<T> clazz, int pageSize, String continuationToken) {
        return this.operation.queryItemsPage(dataPartitionId,  cosmosDBName, collection, query, clazz,  pageSize,  continuationToken);
    }

    @Override
    public void createItem(String dataPartitionId, String cosmosDBName, String collection, @NonNull String partitionKey, T item) {
        Assert.notNull(item, ENTITY_MUST_NOT_BE_NULL);
        this.operation.createItem(dataPartitionId, cosmosDBName, collection, partitionKey, item);
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
    public T save(T entity, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(entity, ENTITY_MUST_NOT_BE_NULL);
        this.upsertItem(dataPartitionId, cosmosDBName, collection, partitionKey, entity);
        return entity;
    }

    /*
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
    */

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
    public List<T> findAllById(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(ids, "Iterable ids should not be null");
        return this.findByIds(ids, dataPartitionId,  cosmosDBName,  collection,  partitionKey);
    }

    @Override
    public List<T> findByIds(Iterable<String> ids, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(ids, "Id list should not be null");
        throw new UnsupportedOperationException();
    }

    @Override
    public List<T> find(String dataPartitionId, String cosmosDBName, String collection, SqlQuerySpec query) {
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return this.queryItems(dataPartitionId, cosmosDBName, collection, query, options);
    }

    @Override
    public T getOne(@NonNull String id, String dataPartitionId, String cosmosDBName, String collection, String partitionKey) {
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);
        Optional<T> doc = this.findItem(dataPartitionId, cosmosDBName, collection, id, partitionKey);
        if (!doc.isPresent())
            return null;
        return doc.get();
    }

    // Finds with Query

    public Page<T> findAll(Pageable pageable, String dataPartitionId, String cosmosDBName, String collectionName) {
        CosmosStoreQuery query = (new CosmosStoreQuery());
        if (pageable.getSort().isSorted()) {
            query.with(pageable.getSort());
        }
        SqlQuerySpec sqlQuerySpec = (new FindQuerySpecGenerator()).generateCosmos(query);
        return this.paginationQuery(pageable, sqlQuerySpec, domainClass, dataPartitionId, cosmosDBName, collectionName);
    }

    public Page<T> find(@NonNull Pageable pageable, String dataPartitionId, String cosmosDBName, String collectionName, SqlQuerySpec query) {
        Assert.notNull(pageable, PAGEABLE_MUST_NOT_BE_NULL);
        CosmosStoreQuery cosmosQuery = (new CosmosStoreQuery()).with(query.getQueryText());
        if (pageable.getSort().isSorted()) {
            cosmosQuery.with(pageable.getSort());
        }
        SqlQuerySpec sqlQuerySpec = (new FindQuerySpecGenerator()).generateCosmosWithQueryText(cosmosQuery, cosmosQuery.getQuery());
        return this.paginationQuery(pageable, sqlQuerySpec, domainClass, dataPartitionId, cosmosDBName, collectionName);
    }

    @Override
    public Iterable<T> findAll(@NonNull Sort sort, String dataPartitionId, String cosmosDBName, String collection) {
        Assert.notNull(sort, "sort of findAll should not be null");
        CosmosStoreQuery query = (new CosmosStoreQuery()).with(sort);
        SqlQuerySpec sqlQuerySpec = (new FindQuerySpecGenerator()).generateCosmos(query);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        //System.out.println("SimpleCosmosStoreRepository sqlQuerySpec.getQueryText()=" + sqlQuerySpec.getQueryText());
        return this.queryItems(dataPartitionId, cosmosDBName, collection, sqlQuerySpec, options);
    }

    private static boolean isUnpaged(Pageable pageable) {
        return pageable.isUnpaged();
    }

    public Page<T> paginationQuery(Pageable pageable, SqlQuerySpec query, Class<T> domainClass, String dataPartitionId, String cosmosDBName, String collectionName) {
        Assert.isTrue(pageable.getPageSize() > 0, "pageable should have page size larger than 0");
        Assert.hasText(collectionName, "collection should not be null, empty or only whitespaces");
        String continuationToken = null;
        if (pageable instanceof CosmosStorePageRequest) {
            continuationToken = ((CosmosStorePageRequest)pageable).getRequestContinuation();
        }
        int pageSize = pageable.getPageSize();
        return this.queryItemsPage(dataPartitionId, cosmosDBName, collectionName, query, domainClass, pageSize, continuationToken);
    }

    protected BulkImportResponse bulkInsert(String dataPartitionId, String cosmosDBName, String collectionName, Collection<T> docs){
        Assert.notNull(docs, "docs should not be null");
        Assert.notNull(dataPartitionId, "dataPartitionId should not be null");
        Assert.notNull(cosmosDBName, "cosmosDBName should not be null");
        Assert.notNull(collectionName, "collectionName should not be null");
        BulkImportResponse response = cosmosBulkStore.bulkInsert(dataPartitionId, cosmosDBName, collectionName, docs, true, true, bulkImportMaxConcurrencyPePartitionRange);
        if(!response.getErrors().isEmpty()){
            List<String> exceptions = new ArrayList<>();
            for(Exception e : response.getErrors()){
                exceptions.add(e.toString());
            }
            throw new AppException(500, "Record creation has failed!", "Failed to import all documents to CosmosDB using bulk executor!", exceptions.toArray(new String[exceptions.size()]));
        }
        return response;
    }
}
