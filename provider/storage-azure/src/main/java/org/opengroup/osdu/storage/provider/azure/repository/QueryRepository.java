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
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.azure.model.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.azure.cosmos.implementation.Utils.decodeBase64String;
import static com.azure.cosmos.implementation.Utils.encodeBase64String;

@Repository
public class QueryRepository implements IQueryRepository {

    private final String cosmosDBName = "osdu-db";
    private final String storageRecordContainer = "StorageRecord";

    @Autowired
    private RecordMetadataRepository record;

    @Autowired
    private SchemaRepository schema;

    private  static final Logger logger = LoggerFactory.getLogger(QueryRepository.class);

    @Autowired
    @Qualifier("CursorCache")
    private ICache<String, String> cursorCache;

    @Autowired
    private DpsHeaders dpsHeaders;

    @Autowired
    private CosmosStore cosmosStore;

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> docs = new ArrayList();
        try {
            /* TODO: PAGINATION REIMPLEMENTATION NEEDED*/
            List<String> allDocs = getDistinctKind();

            if (limit != null) {
                if (cursor == null) {
                    for (int i = 0; i < limit && i < allDocs.size(); i++) {
                        docs.add(allDocs.get(i));
                    }
                    String continuationToken = "start" + limit;
                    cursorCache.put(continuationToken, Integer.toString(limit));
                    dqr.setCursor(continuationToken);
                } else {
                    Integer startIndex = Integer.parseInt(cursorCache.get(cursor));
                    Integer endIndex = startIndex + limit;
                    for (int i = startIndex; i < endIndex && i < allDocs.size(); i++) {
                        docs.add(allDocs.get(i));
                    }
                    if (endIndex < allDocs.size()) {
                        String continuationToken = "start" + endIndex + limit;
                        cursorCache.put(continuationToken, Integer.toString(endIndex));
                        dqr.setCursor(continuationToken);
                    }
                }
            } else {
                docs = allDocs;
            }
            dqr.setResults(docs);
        } catch (CosmosException e) {
            throw e;
        } catch (Exception e) {
            throw e;
        }
        return dqr;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {
        Assert.notNull(kind, "kind must not be null");
        boolean paginated = false;
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }
        String status = RecordState.active.toString();
        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> ids = new ArrayList<>();
        Iterable<RecordMetadataDoc> docs;
        String continuation = cursor;
        int iteration = 1;
        int preferredPageSize;

        try {
            if (paginated) {
                do {
                    preferredPageSize = numRecords - ids.size();
                    // Fetch records and set ids
                    Page<RecordMetadataDoc> docPage = record.findIdsByMetadata_kindAndMetadata_status(kind, status, CosmosStorePageRequest.of(0, preferredPageSize, continuation), collaborationContext);
                    docs = docPage.getContent();
                    docs.forEach(d -> ids.add(d.getId()));

                    if (iteration > 1) {
                        // cosmosDb did not return the preferredPageSize in previous iteration, so it was queried again.
                        this.logger.info(String.format("Iteration count of query on cosmosDb: %d, page size returned: %d, remaining page size: %d", iteration, docPage.getContent().size(), numRecords - ids.size()));
                    }

                    // set continuationToken by fetching it from the response
                    continuation = null;
                    Pageable pageable = docPage.getPageable();
                    if (pageable instanceof CosmosStorePageRequest) {
                        continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                    }
                    iteration++;

                } while (!Strings.isNullOrEmpty(continuation) && ids.size() < numRecords);

                if (!Strings.isNullOrEmpty(continuation)) {
                    dqr.setCursor(continuation);
                }

            } else {
                docs = record.findIdsByMetadata_kindAndMetadata_status(kind, status, collaborationContext);
                docs.forEach(d -> ids.add(d.getId()));
            }
            dqr.setResults(ids);
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.SC_BAD_REQUEST && e.getMessage().contains("INVALID JSON in continuation token"))
                throw this.getInvalidCursorException();
            else throw e;
        } catch (Exception e) {
            throw e;
        }

        return dqr;

    }


    @Override
    public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
        return getQueryResult(getIdsAndKindBy_MetadataStatusQuery(RecordState.active.toString()), limit,cursor, RecordIdAndKind.class);
    }

    @Override
    public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
        return getQueryResult(getIdsBy_Kind_And_MetadataStatusQuery(kind, RecordState.active.toString()), limit, cursor, RecordId.class);
    }

    @Override
    public HashMap<String, Long> getActiveRecordsCount() {
        List<HashMap>  countByActiveRecordList = this.queryItemCount(getRecordCountBy_MetadataStatusQuery(RecordState.active.toString()));
        return new HashMap<String, Long>() {{
            put("*", (Long) countByActiveRecordList.get(0).get("$1"));
        }};
    }

    @Override
    public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
        List<HashMap> countByKindList = this.queryItemCount(getRecordCountBy_Kinds_MetadataStatusQuery(kinds, RecordState.active.toString()));
        return countByKindList.stream()
                .collect(HashMap::new,
                        (map, m) -> map.put(m.get("kind").toString(), (Long) m.get("IdCount")),
                        HashMap::putAll);
    }

    private List<HashMap> queryItemCount(SqlQuerySpec query) {
        return this.cosmosStore.queryItems(dpsHeaders.getPartitionId(), cosmosDBName, storageRecordContainer, query, new CosmosQueryRequestOptions(), HashMap.class);
    }

    public <T> RecordInfoQueryResult<T> getQueryResult(SqlQuerySpec query, Integer limit, String cursor, Class<T> clazz) {
        boolean paginated = false;
        int numRecords = PAGE_SIZE;

        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }

        RecordInfoQueryResult<T> recordQueryResult = new RecordInfoQueryResult<T>();
        recordQueryResult.setResults(new ArrayList<>());
        List<T> docs;
        String continuation = (cursor == null) ? null : decodeBase64String(cursor);
        int iteration = 1;
        int preferredPageSize;
        String encodedCursor = null;

        try {
            if (paginated) {
                do {
                    preferredPageSize = numRecords - recordQueryResult.getResults().size();
                    Page<T> docPage = cosmosStore.queryItemsPage(dpsHeaders.getPartitionId(), cosmosDBName, storageRecordContainer, query, clazz, preferredPageSize, continuation);

                    // Fetch records and set ids
                    docs = docPage.getContent();
                    docs.forEach(x -> recordQueryResult.getResults().add(x));

                    if (iteration > 1) {
                        // cosmosDb did not return the preferredPageSize in previous iteration, so it was queried again.
                        this.logger.info(String.format("Iteration count of query on cosmosDb: %d, page size returned: %d, remaining page size: %d", iteration, docPage.getContent().size(), numRecords - recordQueryResult.getResults().size()));
                    }

                    // set continuationToken by fetching it from the response
                    continuation = null;
                    Pageable pageable = docPage.getPageable();
                    if (pageable instanceof CosmosStorePageRequest) {
                        continuation = ((CosmosStorePageRequest) pageable).getRequestContinuation();
                    }
                    iteration++;
                } while (!Strings.isNullOrEmpty(continuation) && recordQueryResult.getResults().size() < numRecords);

                     if(!Strings.isNullOrEmpty(continuation))
                         encodedCursor = encodeBase64String(continuation.getBytes());

                    recordQueryResult.setCursor(encodedCursor);
            } else {
                docs = cosmosStore.queryItems(dpsHeaders.getPartitionId(), cosmosDBName, storageRecordContainer, query, new CosmosQueryRequestOptions(), clazz);
                recordQueryResult.setResults(docs);
            }
        } catch (CosmosException e) {
            if (e.getStatusCode() == HttpStatus.SC_BAD_REQUEST && e.getMessage().contains("INVALID JSON in continuation token"))
                throw this.getInvalidCursorException();
            else throw e;
        } catch (Exception e) {
            throw e;
        }

        return recordQueryResult;
    }

    private List<String> getDistinctKind() {
        List<String> docs;
        CosmosQueryRequestOptions storageOptions = new CosmosQueryRequestOptions();
        String queryText = String.format("SELECT distinct value c.metadata.kind FROM c");
        SqlQuerySpec query = new SqlQuerySpec(queryText);
        docs = cosmosStore.queryItems(dpsHeaders.getPartitionId(), cosmosDBName, storageRecordContainer, query, storageOptions, String.class);
        return docs;
    }

    private static SqlQuerySpec getIdsAndKindBy_MetadataStatusQuery(String status) {
        SqlQuerySpec query = new SqlQuerySpec("SELECT c.id, c.metadata.kind FROM c WHERE c.metadata.status = @status");
        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@status", status));
        return query;
    }

    private static SqlQuerySpec getRecordCountBy_MetadataStatusQuery(String status) {
        SqlQuerySpec query = new SqlQuerySpec("SELECT count(c.id) FROM c WHERE c.metadata.status = @status");
        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@status", status));
        return query;
    }

    private static SqlQuerySpec getRecordCountBy_Kinds_MetadataStatusQuery(List<String> kinds, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.metadata.kind, COUNT(c.id) AS IdCount " +
                        "FROM c " +
                        "WHERE ARRAY_CONTAINS(@kinds, c.metadata.kind, true) AND c.metadata.status = @status " +
                        "GROUP BY c.metadata.kind"
        );

        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@status", status));
        pars.add(new SqlParameter("@kinds", kinds));

        return query;
    }


    private static SqlQuerySpec getIdsBy_Kind_And_MetadataStatusQuery(String kind, String status) {
        SqlQuerySpec query = new SqlQuerySpec(
                "SELECT c.id " +
                        "FROM c " +
                        "WHERE c.metadata.kind = @kind AND c.metadata.status = @status"
        );

        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@kind", kind));
        pars.add(new SqlParameter("@status", status));

        return query;
    }


    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid", "The requested cursor does not exist or is invalid");
    }
}
