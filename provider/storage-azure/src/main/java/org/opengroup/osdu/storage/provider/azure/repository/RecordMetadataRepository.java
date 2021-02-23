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

package org.opengroup.osdu.storage.provider.azure.repository;


import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.microsoft.azure.documentdb.bulkexecutor.BulkImportResponse;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.*;

@Repository
public class RecordMetadataRepository extends SimpleCosmosStoreRepository<RecordMetadataDoc> implements IRecordsMetadataRepository<String> {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;

    @Autowired
    private CosmosContainerConfig cosmosContainerConfig;

    @Autowired
    private String recordMetadataCollection;

    @Autowired
    private String cosmosDBName;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private int minBatchSizeToUseBulkUpload;

    public RecordMetadataRepository() {
        super(RecordMetadataDoc.class);
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        Assert.notNull(recordsMetadata, "recordsMetadata must not be null");

        if(recordsMetadata.size() >= minBatchSizeToUseBulkUpload) createOrUpdateParallel(recordsMetadata);
        else createOrUpdateSerial(recordsMetadata);

        return recordsMetadata;
    }

    /**
     * Implementation of createOrUpdate that writes the records in serial one at a time to Cosmos.
     * @param recordsMetadata records to write to cosmos.
     */
    private void createOrUpdateSerial(List<RecordMetadata> recordsMetadata){
        for (RecordMetadata recordMetadata : recordsMetadata) {
            RecordMetadataDoc doc = new RecordMetadataDoc();
            doc.setId(recordMetadata.getId());
            doc.setMetadata(recordMetadata);
            this.save(doc);
        }
    }

    /**
     * Implementation of createOrUpdate that uses DocumentBulkExecutor to upload all records in parallel to Cosmos.
     * @param recordsMetadata records to write to cosmos.
     */
    private void createOrUpdateParallel(List<RecordMetadata> recordsMetadata){
        Collection<RecordMetadataDoc> docs = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata){
            RecordMetadataDoc doc = new RecordMetadataDoc();
            doc.setId(recordMetadata.getId());
            doc.setMetadata(recordMetadata);
            docs.add(doc);
        }
        BulkImportResponse response = this.bulkInsert(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, docs);
    }

    @Override
    public RecordMetadata get(String id) {
        RecordMetadataDoc item = this.getOne(id);
        return (item == null) ? null : item.getMetadata();
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, String cursor) {
        List<RecordMetadata> outputRecords = new ArrayList<>();
        String continuation = null;

        Iterable<RecordMetadataDoc> docs;

        long start = System.currentTimeMillis();
        try {
            String queryText = String.format("SELECT * FROM c WHERE ARRAY_CONTAINS(c.metadata.legal.legaltags, '%s')", legalTagName);
            SqlQuerySpec query = new SqlQuerySpec(queryText);;
            final Page<RecordMetadataDoc> docPage = this.find(CosmosStorePageRequest.of(0, limit, cursor), query);
            docs = docPage.getContent();
            docs.forEach(d -> {
                outputRecords.add(d.getMetadata());
            });

            Pageable pageable = docPage.getPageable();
            if (pageable instanceof CosmosStorePageRequest) {
                continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.SC_BAD_REQUEST && e.getMessage().contains("INVALID JSON in continuation token"))
                throw this.getInvalidCursorException();
            else
                throw e;
        } catch (Exception e) {
            throw e;
        }
        long finish = System.currentTimeMillis();
        long timeElapsed = finish - start;
        //System.out.println("!! queryByLegalTagName time elapsed = " + timeElapsed);
        return new AbstractMap.SimpleEntry<>(continuation, outputRecords);
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids) {
        String sqlQueryString = createCosmosBatchGetQueryById(ids);
        SqlQuerySpec query = new SqlQuerySpec(sqlQueryString);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<RecordMetadataDoc> queryResults = this.queryItems(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query, options);

        Map<String, RecordMetadata> results = new HashMap<>();
        for(RecordMetadataDoc doc : queryResults){
            if (doc.getMetadata() == null) continue;
            results.put(doc.getId(), doc.getMetadata());
        }
        return results;
    }

    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }

    public List<RecordMetadataDoc> findByMetadata_kindAndMetadata_status(String kind, String status) {
        Assert.notNull(kind, "kind must not be null");
        Assert.notNull(status, "status must not be null");
        SqlQuerySpec query = getMetadata_kindAndMetada_statusQuery(kind, status);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return this.queryItems(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query, options);
    }

    public Page<RecordMetadataDoc> findByMetadata_kindAndMetadata_status(String kind, String status, Pageable pageable) {
        Assert.notNull(kind, "kind must not be null");
        Assert.notNull(status, "status must not be null");
        SqlQuerySpec query = getMetadata_kindAndMetada_statusQuery(kind, status);
        return this.find(pageable, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query);
    }

    private static SqlQuerySpec getMetadata_kindAndMetada_statusQuery(String kind, String status) {
        String queryText = String.format("SELECT * FROM c WHERE c.metadata.kind = '%s' AND c.metadata.status = '%s'", kind, status);
        SqlQuerySpec query = new SqlQuerySpec(queryText);
        return query;
    }

    public Page<RecordMetadataDoc> find(@NonNull Pageable pageable, SqlQuerySpec query) {
        return this.find(pageable, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query);
    }

    public RecordMetadataDoc getOne(@NonNull String id) {
        return this.getOne(id, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, id);
    }

    public RecordMetadataDoc save(RecordMetadataDoc entity) {
        return this.save(entity, headers.getPartitionId(),cosmosDBName,recordMetadataCollection,entity.getId());
    }

    @Override
    public void delete(String id) {
        this.deleteById(id, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, id);
    }

    /**
     * Method to generate query string for searching Cosmos for a list of Ids.
     * @param ids Ids to generate query for.
     * @return String representing Cosmos query searching for all of the ids.
     */
    private String createCosmosBatchGetQueryById(List<String> ids){
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM c WHERE c.id IN (");
        for(String id : ids){
            sb.append("\"" + id + "\",");
        }

        // remove trailing comma, add closing parenthesis
        sb.deleteCharAt(sb.lastIndexOf(","));
        sb.append(")");
        return sb.toString();
    }
}
