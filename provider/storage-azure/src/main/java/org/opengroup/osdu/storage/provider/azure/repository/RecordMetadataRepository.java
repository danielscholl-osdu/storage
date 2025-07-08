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
import com.azure.cosmos.models.CosmosPatchOperations;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.cosmosdb.CosmosStoreBulkOperations;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.model.RecordMetadataDoc;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.storage.provider.azure.model.DocumentCount;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.RecordConstants.METADATA_PREFIX_PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.MODIFY_TIME_PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.MODIFY_USER_PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.OP;
import static org.opengroup.osdu.storage.util.RecordConstants.PATH;
import static org.opengroup.osdu.storage.util.RecordConstants.VALUE;

@Repository
public class RecordMetadataRepository extends SimpleCosmosStoreRepository<RecordMetadataDoc> implements IRecordsMetadataRepository<String> {

    private static final int AZURE_PATCH_OPERATIONS_LIMIT = 10;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;

    @Autowired
    private CosmosContainerConfig cosmosContainerConfig;

    @Autowired
    private CosmosStoreBulkOperations cosmosBulkStore;

    @Autowired
    private String recordMetadataCollection;

    @Autowired
    private String cosmosDBName;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private int minBatchSizeToUseBulkUpload;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecordMetadataRepository() {
        super(RecordMetadataDoc.class);
    }

    @Override
    public Map<String, String> patch(Map<RecordMetadata, JsonPatch> jsonPatchPerRecord, Optional<CollaborationContext> collaborationContext) {
        String modifyUser;
        Long modifyTime;
        List<CosmosPatchOperations> cosmosPatchOperations;
        Map<String, List<CosmosPatchOperations>> cosmosPatchOperationsPerDoc = new HashMap<>();
        Map<String, String> partitionKeyForDoc = new HashMap<>();
        for (Map.Entry<RecordMetadata, JsonPatch> jsonPatchPerRecordEntry : jsonPatchPerRecord.entrySet()) {
            RecordMetadata recordMetadata = jsonPatchPerRecordEntry.getKey();
            modifyUser = recordMetadata.getModifyUser();
            modifyTime = recordMetadata.getModifyTime();
            cosmosPatchOperations = getCosmosPatchOperations(modifyUser, modifyTime, jsonPatchPerRecordEntry.getValue());
            String docId = CollaborationContextUtil.composeIdWithNamespace(recordMetadata.getId(), collaborationContext);
            cosmosPatchOperationsPerDoc.put(docId, cosmosPatchOperations);
            partitionKeyForDoc.put(docId, recordMetadata.getId());
        }
        Map<String, String> recordIdToError = new HashMap<>();
        try {
            cosmosBulkStore.bulkMultiPatchWithCosmosClient(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, cosmosPatchOperationsPerDoc, partitionKeyForDoc, 1);
        } catch (AppException e) {
            if (e.getOriginalException() instanceof AppException originalException) {
                String[] originalExceptionErrors = originalException.getError().getErrors();
                for (String cosmosError : originalExceptionErrors) {
                    String[] idAndError = cosmosError.split("\\|");
                    //assuming azure library throws an error in the format of "recordId|<message with responseCode>|<exception>"
                    recordIdToError.put(CollaborationContextUtil.getIdWithoutNamespace(idAndError[0], collaborationContext), idAndError[1]);
                }
            } else {
                throw e;
            }
        }
        return recordIdToError;
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        Assert.notNull(recordsMetadata, "recordsMetadata must not be null");
        recordsMetadata.forEach(metadata -> {
            if (metadata.getAcl() == null) {
                logger.error("Acl of the record " + metadata + " must not be null");
                throw new IllegalArgumentException("Acl of the record must not be null");
            }
        });

        if (recordsMetadata.size() >= minBatchSizeToUseBulkUpload)
            createOrUpdateParallel(recordsMetadata, collaborationContext);
        else createOrUpdateSerial(recordsMetadata, collaborationContext);

        return recordsMetadata;
    }

    /**
     * Implementation of createOrUpdate that writes the records in serial one at a time to Cosmos.
     *
     * @param recordsMetadata records to write to cosmos.
     */
    private void createOrUpdateSerial(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        for (RecordMetadata recordMetadata : recordsMetadata) {
            RecordMetadataDoc doc = new RecordMetadataDoc();
            doc.setId(CollaborationContextUtil.composeIdWithNamespace(recordMetadata.getId(), collaborationContext));
            doc.setMetadata(recordMetadata);
            this.save(doc);
        }
    }

    /**
     * Implementation of createOrUpdate that uses CosmosClient to upload all records in parallel to Cosmos.
     *
     * @param recordsMetadata records to write to cosmos.
     */
    private void createOrUpdateParallel(List<RecordMetadata> recordsMetadata, Optional<CollaborationContext> collaborationContext) {
        List<RecordMetadataDoc> docs = new ArrayList<>();
        List<String> partitionKeys = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            RecordMetadataDoc doc = new RecordMetadataDoc();
            doc.setId(CollaborationContextUtil.composeIdWithNamespace(recordMetadata.getId(), collaborationContext));
            doc.setMetadata(recordMetadata);
            docs.add(doc);
            partitionKeys.add(recordMetadata.getId());
        }

        cosmosBulkStore.bulkInsertWithCosmosClient(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, docs, partitionKeys, 1);
    }

    @Override
    public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
        RecordMetadataDoc item = this.getOne(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext));
        return (item == null) ? null : item.getMetadata();
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {
        return null;
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, String cursor) {
        String[] legalTagNames = {legalTagName};
        return queryByLegalTagName(legalTagNames, limit, cursor);
    }

    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String[] legalTagNames, int limit, String cursor) {
        List<RecordMetadata> outputRecords = new ArrayList<>();
        String continuation = null;

        Iterable<RecordMetadataDoc> docs;

        try {
            String legalTagNamesString = StreamSupport.stream(Arrays.spliterator(legalTagNames), false)
                .map(tag -> String.format("'%s'", tag))
                .collect(Collectors.joining(", "));

            String queryText = String.format("SELECT * FROM c WHERE ARRAY_CONTAINS_ANY(c.metadata.legal.legaltags, %s)", legalTagNamesString);
            SqlQuerySpec query = new SqlQuerySpec(queryText);
            final Page<RecordMetadataDoc> docPage = this.find(CosmosStorePageRequest.of(0, limit, cursor), query);
            docs = docPage.getContent();
            docs.forEach(d -> outputRecords.add(d.getMetadata()));

            Pageable pageable = docPage.getPageable();
            if (pageable instanceof CosmosStorePageRequest cosmosStorePageRequest) {
                continuation = cosmosStorePageRequest.getRequestContinuation();
            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.SC_BAD_REQUEST && e.getMessage().contains("INVALID JSON in continuation token"))
                throw this.getInvalidCursorException();
            else
                throw e;
        } catch (Exception e) {
            throw e;
        }

        return new AbstractMap.SimpleEntry<>(continuation, outputRecords);
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids, Optional<CollaborationContext> collaborationContext) {
        String sqlQueryString = createCosmosBatchGetQueryById(ids, collaborationContext);
        SqlQuerySpec query = new SqlQuerySpec(sqlQueryString);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<RecordMetadataDoc> queryResults = queryCosmosItems(query, options);

        Map<String, RecordMetadata> results = new HashMap<>();
        for (RecordMetadataDoc doc : queryResults) {
            if (doc.getMetadata() == null) continue;
            results.put(doc.getId(), doc.getMetadata());
        }
        return results;
    }

    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }

    public List<RecordMetadataDoc> findIdsByMetadata_kindAndMetadata_status(String kind, String status, Optional<CollaborationContext> collaborationContext) {
        Assert.notNull(kind, "kind must not be null");
        Assert.notNull(status, "status must not be null");
        SqlQuerySpec query = getIdsByMetadata_kindAndMetada_statusQuery(kind, status, collaborationContext);
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        return queryCosmosItems(query, options);
    }

    public Page<RecordMetadataDoc> findIdsByMetadata_kindAndMetadata_status(String kind, String status, Pageable pageable, Optional<CollaborationContext> collaborationContext) {
        Assert.notNull(kind, "kind must not be null");
        Assert.notNull(status, "status must not be null");
        SqlQuerySpec query = getIdsByMetadata_kindAndMetada_statusQuery(kind, status, collaborationContext);
        CosmosQueryRequestOptions queryOptions = new CosmosQueryRequestOptions();
        queryOptions.setResponseContinuationTokenLimitInKb(1);
        return this.find(pageable, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query, queryOptions);
    }

    public int getMetadataDocumentCountForBlob(String path) {
        Assert.notNull(path, "path must not be null");
        String sqlQueryString = String.format("SELECT COUNT(1) AS documentCount from c WHERE ARRAY_CONTAINS (c.metadata.gcsVersionPaths, '%s')", path);
        SqlQuerySpec query = new SqlQuerySpec(sqlQueryString);
        List<DocumentCount> queryResponse = this.queryItems(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query, new CosmosQueryRequestOptions(), DocumentCount.class);
        return queryResponse == null || queryResponse.isEmpty() ? 0 : queryResponse.get(0).getDocumentCount();
    }

    private static SqlQuerySpec getIdsByMetadata_kindAndMetada_statusQuery(String kind, String status, Optional<CollaborationContext> collaborationContext) {
        String queryText;
        if (!collaborationContext.isPresent()) {
            queryText = String.format("SELECT c.metadata.id FROM c WHERE c.metadata.kind = '%s' AND c.metadata.status = '%s' AND c.id = c.metadata.id", kind, status);
        } else {
            queryText = String.format("SELECT c.metadata.id FROM c WHERE c.metadata.kind = '%s' AND c.metadata.status = '%s' and STARTSWITH(c.id, '%s')", kind, status, CollaborationContextUtil.getNamespace(collaborationContext));
        }
        return new SqlQuerySpec(queryText);
    }

    public Page<RecordMetadataDoc> find(@NonNull Pageable pageable, SqlQuerySpec query) {
        return this.find(pageable, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query);
    }

    public RecordMetadataDoc getOne(@NonNull String id) {
        return this.getOne(id, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, id);
    }

    public RecordMetadataDoc save(RecordMetadataDoc entity) {
        return this.save(entity, headers.getPartitionId(), cosmosDBName, recordMetadataCollection, entity.getId());
    }

    @Override
    public void delete(String id, Optional<CollaborationContext> collaborationContext) {
        this.deleteById(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext), headers.getPartitionId(), cosmosDBName, recordMetadataCollection, CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext));
    }

    /**
     * Method to generate query string for searching Cosmos for a list of Ids.
     *
     * @param ids Ids to generate query for.
     * @return String representing Cosmos query searching for all of the ids.
     */
    private String createCosmosBatchGetQueryById(List<String> ids, Optional<CollaborationContext> collaborationContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM c WHERE c.id IN (");
        for (String id : ids) {
            sb.append("\"" + CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext) + "\",");
        }

        // remove trailing comma, add closing parenthesis
        sb.deleteCharAt(sb.lastIndexOf(","));
        sb.append(")");
        return sb.toString();
    }

    private List<CosmosPatchOperations> getCosmosPatchOperations(String modifyUser, Long modifyTime, JsonPatch jsonPatch) {
        List<CosmosPatchOperations> patchOperationsList = new ArrayList<>();
        List<JsonNode> patchNodes = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false).toList();

        List<List<JsonNode>> patchNodesSubList = Lists.partition(patchNodes, AZURE_PATCH_OPERATIONS_LIMIT);
        patchNodesSubList.forEach(sublist -> {
            CosmosPatchOperations cosmosPatchOperations = CosmosPatchOperations.create();
            sublist.forEach(patchOp -> {
                switch (patchOp.get(OP).textValue()) {
                    case "add" ->
                            cosmosPatchOperations.add(METADATA_PREFIX_PATH + patchOp.get(PATH).textValue(), patchOp.get(VALUE));
                    case "replace" ->
                            cosmosPatchOperations.replace(METADATA_PREFIX_PATH + patchOp.get(PATH).textValue(), patchOp.get(VALUE));
                    case "remove" -> cosmosPatchOperations.remove(METADATA_PREFIX_PATH + patchOp.get(PATH).textValue());
                }
            });
            patchOperationsList.add(cosmosPatchOperations);
        });

        CosmosPatchOperations cosmosPatchOperations = CosmosPatchOperations.create();
        cosmosPatchOperations.replace(METADATA_PREFIX_PATH + MODIFY_USER_PATH, modifyUser);
        cosmosPatchOperations.replace(METADATA_PREFIX_PATH + MODIFY_TIME_PATH, modifyTime);
        patchOperationsList.add(cosmosPatchOperations);

        return patchOperationsList;
    }

    private List<RecordMetadataDoc> queryCosmosItems(SqlQuerySpec query, CosmosQueryRequestOptions options){
        List<RecordMetadataDoc> queryResults;

        try {
            queryResults = this.queryItems(headers.getPartitionId(), cosmosDBName, recordMetadataCollection, query, options);
        } catch (AppException e){
            if (e.getError().getCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR)
                throw new AppException(HttpStatus.SC_SERVICE_UNAVAILABLE, "Error reaching Cosmos DB service.", e.getMessage(), e);
            else
                throw e;
        }

        return queryResults;
    }
}
