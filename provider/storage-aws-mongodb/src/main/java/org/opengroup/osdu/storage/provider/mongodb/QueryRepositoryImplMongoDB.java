// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.mongodb;

import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.aws.mongodb.MongoDBHelper;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents.RecordMetadataDocMongoDB;
import org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents.SchemaMongoDBDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class QueryRepositoryImplMongoDB implements IQueryRepository {

    private DpsHeaders headers;
    private MongoDBHelper queryHelper;

    @Autowired
    public QueryRepositoryImplMongoDB(DpsHeaders headers, MongoDBHelper queryHelper) {
        this.headers = headers;
        this.queryHelper = queryHelper;
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
        // Set the page size or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult datastoreQueryResult = new DatastoreQueryResult();

        Criteria criteria = Criteria.where("dataPartitionId").is(headers.getPartitionId());
        criteria.andOperator(Criteria.where("user").is(headers.getUserEmail()));
        Query legalTagQuery = new Query(criteria);

        QueryPageResult<SchemaMongoDBDoc> queryPageResult = queryHelper.queryPage(SchemaMongoDBDoc.class, legalTagQuery, "kind", cursor, numRecords);

        List<String> kinds = new ArrayList<>();
        for (SchemaMongoDBDoc schemaDoc : queryPageResult.results) {
            kinds.add(schemaDoc.getKind());
        }

        // Set the cursor for the next page, if applicable
        datastoreQueryResult.setCursor(queryPageResult.cursor);

        //TODO: maybe sort only if null cursor
        Collections.sort(kinds);
        datastoreQueryResult.setResults(kinds);
        return datastoreQueryResult;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor) {
        // Set the page size, or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> ids = new ArrayList<>();


        Criteria criteria = Criteria.where("kind").is(kind);
        criteria.andOperator(Criteria.where("status").is("active"));
        Query legalTagQuery = new Query(criteria);
        QueryPageResult<RecordMetadataDocMongoDB> scanPageResults = queryHelper.queryPage(RecordMetadataDocMongoDB.class, legalTagQuery, "id", cursor, numRecords);

        dqr.setCursor(scanPageResults.cursor); // set the cursor for the next page, if applicable
        scanPageResults.results.forEach(schemaDoc -> ids.add(schemaDoc.getId())); // extract the Kinds from the SchemaDocs

        // Sort the IDs alphabetically and set the results
        Collections.sort(ids);
        dqr.setResults(ids);
        return dqr;
    }
}