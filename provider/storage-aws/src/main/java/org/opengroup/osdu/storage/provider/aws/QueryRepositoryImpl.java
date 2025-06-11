/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.GsiQueryRequest;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.QueryPageResult;
import org.opengroup.osdu.core.aws.v2.dynamodb.util.RequestBuilderUtil;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.service.AwsSchemaServiceImpl;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.function.Function;

@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "dynamodb",
        matchIfMissing = true)
@Repository
public class QueryRepositoryImpl implements IQueryRepository {

    public static final String STATUS = "Status";
    public static final String ERROR_PARSING_RESULTS = "Error parsing results";
    public static final String ACTIVE = "active";
    private static final String SCHEMA_GSI_DATAPARTITION_INDEX_NAME = "DataPartitionId-User-Index";
    private static final String RECORD_GSI_KINDSTATUS_INDEX_NAME = "KindStatusIndex";
    final DpsHeaders headers;

    private final org.opengroup.osdu.core.common.logging.JaxRsDpsLog logger;

    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    
    AwsSchemaServiceImpl schemaService;

    @Value("${aws.dynamodb.schemaRepositoryTable.ssm.relativePath}")
    String schemaRepositoryTableParameterRelativePath;    

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    public QueryRepositoryImpl(DpsHeaders headers, JaxRsDpsLog logger, DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory, AwsSchemaServiceImpl schemaService) {
        this.headers = headers;
        this.logger = logger;
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.schemaService = schemaService;
    }

    private DynamoDBQueryHelper<SchemaDoc> getSchemaTableQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, schemaRepositoryTableParameterRelativePath, SchemaDoc.class);
    }    

    private DynamoDBQueryHelper<RecordMetadataDoc> getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.createQueryHelper(headers, recordMetadataTableParameterRelativePath, RecordMetadataDoc.class);
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

        DynamoDBQueryHelper<SchemaDoc> schemaTableQueryHelper = getSchemaTableQueryHelper();

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
            // Build QueryEnhancedRequest with RequestBuilderUtil
            GsiQueryRequest<SchemaDoc> queryRequest =
                    RequestBuilderUtil.QueryRequestBuilder.forQuery(queryObject, SCHEMA_GSI_DATAPARTITION_INDEX_NAME, SchemaDoc.class)
                            .limit(numRecords)
                            .cursor(cursor)
                            .buildGsiRequest();

            queryPageResult = schemaTableQueryHelper.queryByGSI(queryRequest, true);
            for (SchemaDoc schemaDoc : queryPageResult.getItems()) {
                kinds.add(schemaDoc.getKind());
            }
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR_PARSING_RESULTS,
                    e.getMessage(), e);
        }

        // Sort the Kinds alphabetically and set the results
        Collections.sort(kinds);
        datastoreQueryResult.setResults(kinds);
        return datastoreQueryResult;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {
        // Set the page size, or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult dqr = new DatastoreQueryResult();

        String idPrefix = collaborationContext
                .map(context -> context.getId() + this.headers.getPartitionId())
                .orElse(this.headers.getPartitionId());

        QueryPageResult<RecordMetadataDoc> scanPageResults;
        try {
            scanPageResults = queryRecordMetadataByKind(
                kind,
                String.format("%s:", idPrefix),
                numRecords,
                cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR_PARSING_RESULTS,
                    e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, e.toString(), e.getMessage());
        }
        dqr.setCursor(scanPageResults.getNextCursor()); // set the cursor for the next page, if applicable
        Function<RecordMetadataDoc, String> metadataMapper = collaborationContext
            .map(context -> (Function<RecordMetadataDoc, String>) (recordMetadataDoc -> recordMetadataDoc.getId().replaceFirst(String.format("^%s", context.getId()), "")))
            .orElse(RecordMetadataDoc::getId);
        List<String> ids = scanPageResults.getItems().stream().map(metadataMapper).sorted().toList(); // extract and sort the Ids from the RecordMetadata Query Results

        dqr.setResults(ids);
        return dqr;
    }

    @Override
    public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor)
    throws IllegalArgumentException{
        // After discussion, this function will be disabled as AWS have another implementation that is more efficient.
        throw new UnsupportedOperationException("Method not implemented.");
    }

    @Override
    public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
        // Set the page size or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
        
        RecordInfoQueryResult<RecordId> result = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        
        try {
            QueryPageResult<RecordMetadataDoc> scanPageResults = queryRecordMetadataByKind(
                kind,
                String.format("%s:", headers.getPartitionId()),
                numRecords,
                cursor);
            
            // Always initialize the results list, even if empty
            result.setResults(records);
            
            // Always set the cursor, even if null
            result.setCursor(scanPageResults.getNextCursor());
            
            // Convert to RecordId objects if we have results
            if (scanPageResults.getItems() != null) {
                for (RecordMetadataDoc doc : scanPageResults.getItems()) {
                    RecordId id = new RecordId();
                    id.setId(doc.getId());
                    // RecordId doesn't have setKind method, so we can't set it here
                    records.add(id);
                }
            }
            
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, ERROR_PARSING_RESULTS,
                    e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, e.toString(), e.getMessage());
        }
        
        return result;
    }

    @Override
    public HashMap<String, Long> getActiveRecordsCount() {
        // First, get all distinct kinds from the schema service
        HashMap<String, Long> kindCounts = new HashMap<>();
        
        try {
            // Get all kinds from schema service
            List<String> kinds = schemaService.getAllKinds();
            
            // Now count active records for each kind
            for (String kind : kinds) {
                long count = getActiveRecordCountForKind(kind);
                if (count > 0) {
                    kindCounts.put(kind, count);
                }
            }
            
            return kindCounts;
            
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error retrieving active records count",
                    e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to get active record count for a specific kind
     * Filters by both active status and the current partition ID
     */
    private long getActiveRecordCountForKind(String kind) {
        try {
            // Count active records for this kind that belong to the current partition
            long count = 0;
            String cursor = null;
            
            do {
                QueryPageResult<RecordMetadataDoc> queryPageResult = queryRecordMetadataByKind(
                    kind,
                    String.format("%s:", headers.getPartitionId()),
                    1000,
                    cursor);
                
                count += queryPageResult.getItems().size();
                cursor = queryPageResult.getNextCursor();
            } while (cursor != null && !cursor.isEmpty());
            return count;
        } catch (UnsupportedEncodingException e) {
            logger.error("Error counting records for kind " + kind + ": " + e.getMessage(), e);
            return 0;
        } catch (IllegalArgumentException e) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, e.toString(), e.getMessage());
        }
    }

    @Override
    public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
        Map<String, Long> kindCounts = new HashMap<>();
        
        for (String kind : kinds) {
            try {
                // Use the same helper method as getActiveRecordsCount to ensure consistency
                long count = getActiveRecordCountForKind(kind);
                kindCounts.put(kind, count);
            } catch (Exception e) {
                logger.error("Error counting records for kind " + kind + ": " + e.getMessage(), e);
                kindCounts.put(kind, 0L);
            }
        }
        
        return kindCounts;
    }
    
    /**
     * Helper method to query record metadata by kind with consistent parameters
     * 
     * @param kind The kind to filter by
     * @param idPrefix The ID prefix to filter by
     * @param limit The maximum number of records to return
     * @param cursor The pagination cursor
     * @return QueryPageResult containing the results and next cursor
     * @throws UnsupportedEncodingException If there's an error encoding the cursor
     */
    private QueryPageResult<RecordMetadataDoc> queryRecordMetadataByKind(
            String kind,
            String idPrefix,
            int limit,
            String cursor) throws UnsupportedEncodingException {

        DynamoDBQueryHelper<RecordMetadataDoc> recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        // Set GSI hash key
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);

        Map<String, AttributeValue> idExpressionValues = new HashMap<>();
        idExpressionValues.put(":IdValue", AttributeValue.builder().s(idPrefix).build());
        String idFilterExpression = new RequestBuilderUtil.ExpressionBuilder().generateFilterExpression("Id", "IdValue", ComparisonOperator.BEGINS_WITH);


        GsiQueryRequest<RecordMetadataDoc> gsiRequest =
                RequestBuilderUtil.QueryRequestBuilder.forQuery(recordMetadataKey, RECORD_GSI_KINDSTATUS_INDEX_NAME, RecordMetadataDoc.class)
                        .limit(limit)
                        .cursor(cursor)
                        .rangeKeyValue(ACTIVE)
                        .filterExpression(idFilterExpression, idExpressionValues)
                        .buildGsiRequest();

        QueryPageResult<RecordMetadataDoc> result = recordMetadataQueryHelper.queryByGSI(gsiRequest);

        // Ensure we always have a valid results list, even if empty
        if (result.getItems() == null) {
            result = new QueryPageResult<>(new ArrayList<>(), result.getLastEvaluatedKey(), result.getNextCursor());
        }
        
        return result;
    }
}
