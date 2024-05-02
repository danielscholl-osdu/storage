// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import jakarta.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.util.*;

@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "dynamodb",
        matchIfMissing = true)
@Repository
public class QueryRepositoryImpl implements IQueryRepository {

    @Inject
    DpsHeaders headers;    

    @Inject
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Value("${aws.dynamodb.schemaRepositoryTable.ssm.relativePath}")
    String schemaRepositoryTableParameterRelativePath;    

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    private DynamoDBQueryHelperV2 getSchemaTableQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, schemaRepositoryTableParameterRelativePath);
    }    

    private DynamoDBQueryHelperV2 getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, recordMetadataTableParameterRelativePath);
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

        DynamoDBQueryHelperV2 schemaTableQueryHelper = getSchemaTableQueryHelper();

        // Set the page size or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult datastoreQueryResult = new DatastoreQueryResult();
        QueryPageResult<SchemaDoc> queryPageResult;
        List<String> kinds = new ArrayList<>();

        try {
            // Query by DataPartitionId global secondary index with User range key
            SchemaDoc queryObject = new SchemaDoc();
            queryObject.setDataPartitionId(headers.getPartitionId());
            queryPageResult = schemaTableQueryHelper.queryByGSI(SchemaDoc.class, queryObject, numRecords, cursor);

            for (SchemaDoc schemaDoc : queryPageResult.results) {
                kinds.add(schemaDoc.getKind());
            }
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }

        // Set the cursor for the next page, if applicable
        datastoreQueryResult.setCursor(queryPageResult.cursor);

        // Sort the Kinds alphabetically and set the results
        Collections.sort(kinds);
        datastoreQueryResult.setResults(kinds);
        return datastoreQueryResult;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {

        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        

        // Set the page size, or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> ids = new ArrayList<>();

        // Set GSI hash key
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);

        String idPrefix = collaborationContext
                .map(context -> context.getId() + this.headers.getPartitionId())
                .orElse(this.headers.getPartitionId());

        QueryPageResult<RecordMetadataDoc> scanPageResults;
        try {
            scanPageResults = recordMetadataQueryHelper.queryPage(
                RecordMetadataDoc.class,
                recordMetadataKey,
                "Status",
                "active",
                "Id",
                ComparisonOperator.BEGINS_WITH,
                String.format("%s:", idPrefix),
                numRecords,
                cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
        dqr.setCursor(scanPageResults.cursor); // set the cursor for the next page, if applicable
        scanPageResults.results.forEach(schemaDoc -> ids.add(schemaDoc.getMetadata().getId())); // extract the Kinds from the SchemaDocs

        // Sort the IDs alphabetically and set the results
        Collections.sort(ids);
        dqr.setResults(ids);
        return dqr;
    }

    @Override
    public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
        return null;
    }

    @Override
    public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
        throw new NotImplementedException();
    }

    @Override
    public HashMap<String, Long> getActiveRecordsCount() {
        throw new  NotImplementedException();
    }

    @Override
    public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
        throw new  NotImplementedException();
    }
}
