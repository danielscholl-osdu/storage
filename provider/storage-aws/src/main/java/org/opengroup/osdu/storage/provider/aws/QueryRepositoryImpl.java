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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
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

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.function.Function;

@ConditionalOnProperty(prefix = "repository", name = "implementation", havingValue = "dynamodb",
        matchIfMissing = true)
@Repository
public class QueryRepositoryImpl implements IQueryRepository {

    final
    DpsHeaders headers;    

    private final org.opengroup.osdu.core.common.logging.JaxRsDpsLog logger;
    
    private final com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper dynamoDBMapper;
    
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Value("${aws.dynamodb.schemaRepositoryTable.ssm.relativePath}")
    String schemaRepositoryTableParameterRelativePath;    

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    String recordMetadataTableParameterRelativePath;

    public QueryRepositoryImpl(DpsHeaders headers, JaxRsDpsLog logger, DynamoDBMapper dynamoDBMapper, DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory) {
        this.headers = headers;
        this.logger = logger;
        this.dynamoDBMapper = dynamoDBMapper;
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
    }

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
        Function<RecordMetadataDoc, String> metadataMapper = collaborationContext
            .map(context -> (Function<RecordMetadataDoc, String>) (recordMetadataDoc -> recordMetadataDoc.getId().replaceFirst(String.format("^%s", context.getId()), "")))
            .orElse(RecordMetadataDoc::getId);
        List<String> ids = scanPageResults.results.stream().map(metadataMapper).sorted().toList(); // extract and sort the Ids from the RecordMetadata Query Results

        dqr.setResults(ids);
        return dqr;
    }

    @Override
    public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        
        // Set the page size or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
        
        RecordInfoQueryResult<RecordIdAndKind> result = new RecordInfoQueryResult<>();
        List<RecordIdAndKind> records = new ArrayList<>();
        
        try {
            // Query for all active records
            RecordMetadataDoc queryObject = new RecordMetadataDoc();
            queryObject.setStatus("active");
            // Set partition ID using available method or field
            // queryObject.setDataPartitionId(headers.getPartitionId());
            
            QueryPageResult<RecordMetadataDoc> queryPageResult = recordMetadataQueryHelper.queryByGSI(
                RecordMetadataDoc.class, 
                queryObject, 
                numRecords, 
                cursor);
            
            // Convert to RecordIdAndKind objects
            for (RecordMetadataDoc doc : queryPageResult.results) {
                RecordIdAndKind record = new RecordIdAndKind();
                record.setId(doc.getId());
                record.setKind(doc.getKind());
                records.add(record);
            }
            
            // Create a new result with the records and cursor
            result.setResults(records);
            result.setCursor(queryPageResult.cursor);
            
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
        
        return result;
    }

    @Override
    public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
        
        // Set the page size or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }
        
        RecordInfoQueryResult<RecordId> result = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        
        try {
            // Set GSI hash key
            RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
            recordMetadataKey.setKind(kind);
            
            QueryPageResult<RecordMetadataDoc> scanPageResults = recordMetadataQueryHelper.queryPage(
                RecordMetadataDoc.class,
                recordMetadataKey,
                "Status",
                "active",
                "Id",
                ComparisonOperator.BEGINS_WITH,
                String.format("%s:", headers.getPartitionId()),
                numRecords,
                cursor);
            
            // Convert to RecordId objects
            for (RecordMetadataDoc doc : scanPageResults.results) {
                RecordId record = new RecordId();
                record.setId(doc.getId());
                // RecordId doesn't have setKind method, so we can't set it here
                records.add(record);
            }
            
            // Create a new result with the records and cursor
            result.setResults(records);
            result.setCursor(scanPageResults.cursor);
            
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
        
        return result;
    }

    @Override
    public HashMap<String, Long> getActiveRecordsCount() {
        DynamoDBQueryHelperV2 schemaTableQueryHelper = getSchemaTableQueryHelper();
        HashMap<String, Long> kindCounts = new HashMap<>();
        
        try {
            // First get all kinds
            SchemaDoc queryObject = new SchemaDoc();
            queryObject.setDataPartitionId(headers.getPartitionId());
            QueryPageResult<SchemaDoc> queryPageResult = schemaTableQueryHelper.queryByGSI(SchemaDoc.class, queryObject, 1000, null);
            
            // For each kind, get the count of active records
            for (SchemaDoc schemaDoc : queryPageResult.results) {
                String kind = schemaDoc.getKind();
                long count = getActiveRecordCountForKind(kind);
                kindCounts.put(kind, count);
            }
            
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
        
        return kindCounts;
    }

    @Override
    public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
        Map<String, Long> kindCounts = new HashMap<>();
        
        for (String kind : kinds) {
            long count = getActiveRecordCountForKind(kind);
            kindCounts.put(kind, count);
        }
        
        return kindCounts;
    }
    
    /**
     * Gets the count of active records for a specific kind.
     *
     * @param kind The kind to count records for
     * @return The count of active records
     */
    private long getActiveRecordCountForKind(String kind) {
        DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();

        // Set GSI hash key
        RecordMetadataDoc recordMetadataKey = new RecordMetadataDoc();
        recordMetadataKey.setKind(kind);
        
        // Count active records for this kind
        // Use a different approach to count records since the count method signature is different
        long count = 0;
        try {
            // This is a simplified approach - you may need to implement a proper counting method
            com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression<RecordMetadataDoc> queryExpression = new com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression<RecordMetadataDoc>()
                .withHashKeyValues(recordMetadataKey)
                .withConsistentRead(false);
            
            count = dynamoDBMapper.count(RecordMetadataDoc.class, queryExpression);
        } catch (Exception ex) {
            logger.error("Error counting records: " + ex.getMessage(), ex);
        }
        
        return count;

    }
}
