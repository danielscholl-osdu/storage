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
import com.google.common.base.Strings;
import com.lambdaworks.redis.RedisException;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.azure.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.azure.SchemaDoc;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.opengroup.osdu.azure.query.CosmosStorePageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Repository
public class QueryRepository implements IQueryRepository {

    private final String cosmosDBName = "osdu-db";
    private final String storageRecordContainer = "StorageRecord";

    @Autowired
    private RecordMetadataRepository record;

    @Autowired
    private SchemaRepository schema;

    @Autowired
    private JaxRsDpsLog logger;

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
                    String continuationToken = "start" + Integer.toString(limit);
                    cursorCache.put(continuationToken, Integer.toString(limit));
                    dqr.setCursor(continuationToken);
                } else {
                    Integer startIndex = Integer.parseInt(cursorCache.get(cursor));
                    Integer endIndex = startIndex + limit;
                    for (int i = startIndex; i < endIndex && i < allDocs.size(); i++) {
                        docs.add(allDocs.get(i));
                    }
                    if (endIndex < allDocs.size()) {
                        String continuationToken = "start" + Integer.toString(endIndex) + Integer.toString(limit);
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
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String hashedCursorKey) {
        Assert.notNull(kind, "kind must not be null");

        boolean paginated = false;
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        String cursor = null;
        if (hashedCursorKey != null && !hashedCursorKey.isEmpty()) {
            paginated = true;
            try {
                cursor = this.cursorCache.get(hashedCursorKey);
            } catch (RedisException ex) {
                this.logger.error(String.format("Error getting key %s from redis: %s", hashedCursorKey, ex.getMessage()), ex);
            }

            if (Strings.isNullOrEmpty(cursor)) throw this.getInvalidCursorException();
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
                    Page<RecordMetadataDoc> docPage = record.findIdsByMetadata_kindAndMetadata_status(kind, status, CosmosStorePageRequest.of(0, preferredPageSize, continuation));
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

                // Hash the continuationToken
                if (!Strings.isNullOrEmpty(continuation)) {
                    String hashedCursor = Crc32c.hashToBase64EncodedString(continuation);
                    this.cursorCache.put(hashedCursor, continuation);
                    dqr.setCursor(hashedCursor);
                }

            } else {
                docs = record.findIdsByMetadata_kindAndMetadata_status(kind, status);
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

    private List<String> getDistinctKind() {
        List<String> docs;
        CosmosQueryRequestOptions storageOptions = new CosmosQueryRequestOptions();
        String queryText = String.format("SELECT distinct value c.metadata.kind FROM c");
        SqlQuerySpec query = new SqlQuerySpec(queryText);
        docs = cosmosStore.queryItems(dpsHeaders.getPartitionId(), cosmosDBName, storageRecordContainer, query, storageOptions, String.class);
        return docs;
    }

    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid", "The requested cursor does not exist or is invalid");
    }
}
