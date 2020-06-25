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

package org.opengroup.osdu.storage.provider.azure;

import com.azure.data.cosmos.CosmosClientException;
import com.azure.data.cosmos.internal.Utils;
import com.azure.data.cosmos.internal.query.QueryItem;
import com.microsoft.azure.spring.data.cosmosdb.core.query.DocumentDbPageRequest;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.azure.util.QueryItemMixIn;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {
    @Autowired
    private CosmosDBRecord dbRecord;

    @Autowired
    private CosmosDBSchema dbSchema;

    QueryRepositoryImpl() {
        Utils.getSimpleObjectMapper().addMixIn(QueryItem.class, QueryItemMixIn.class);
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor)
    {
        boolean paginated  = false;

        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }

        Sort sort = Sort.by(Sort.Direction.ASC, "kind");
        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> kinds = new ArrayList();
        Iterable<SchemaDoc> docs;

        try {
            if (paginated) {
                final Page<SchemaDoc> docPage =
                        dbSchema.findAll(DocumentDbPageRequest.of(0, numRecords, cursor, sort));
                Pageable pageable = docPage.getPageable();
                String continuation = ((DocumentDbPageRequest) pageable).getRequestContinuation();
                dqr.setCursor(continuation);
                docs = docPage.getContent();
            } else {
                docs = dbSchema.findAll(sort);
            }
            docs.forEach(d -> kinds.add(d.getKind()));
            dqr.setResults(kinds);
        } catch (Exception e) {
            if (e.getCause() instanceof CosmosClientException) {
                CosmosClientException ce = (CosmosClientException) e.getCause();
                if(ce.statusCode() == HttpStatus.SC_BAD_REQUEST && ce.getMessage().contains("Invalid Continuation Token"))
                    throw this.getInvalidCursorException();
            }
        }

        return dqr;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(
            String kind, Integer limit, String cursor)
    {
        boolean paginated  = false;

        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
            paginated = true;
        }

        if (cursor != null && !cursor.isEmpty()) {
            paginated = true;
        }

        String status = RecordState.active.toString();
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> ids = new ArrayList();
        Iterable<RecordMetadataDoc> docs;

        try {
            if (paginated) {
                // sorting doesn't work with pagination at the moment due to this:
                // https://github.com/microsoft/spring-data-cosmosdb/issues/423
                final Page<RecordMetadataDoc> docPage = dbRecord.findByMetadata_kindAndMetadata_status(kind, status,
                        DocumentDbPageRequest.of(0, numRecords, cursor));
                Pageable pageable = docPage.getPageable();
                String continuation = ((DocumentDbPageRequest) pageable).getRequestContinuation();
                dqr.setCursor(continuation);
                docs = docPage.getContent();
            } else {
                docs = dbRecord.findByMetadata_kindAndMetadata_status(kind, status);
            }
            docs.forEach(d -> ids.add(d.getId()));
            dqr.setResults(ids);
        } catch (Exception e) {
        if (e.getCause() instanceof CosmosClientException) {
            CosmosClientException ce = (CosmosClientException) e.getCause();
            if(ce.statusCode() == HttpStatus.SC_BAD_REQUEST && ce.getMessage().contains("Invalid Continuation Token"))
                throw this.getInvalidCursorException();
            }
        }

        return dqr;
    }

    private AppException getInvalidCursorException() {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid");
    }
}
