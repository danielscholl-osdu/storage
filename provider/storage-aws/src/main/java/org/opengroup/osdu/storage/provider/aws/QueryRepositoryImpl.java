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

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.aws.exceptions.InvalidCursorException;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {

    @Value("${aws.dynamodb.table.prefix}")
    String tablePrefix;

    @Value("${aws.region}")
    String dynamoDbRegion;

    @Value("${aws.dynamodb.endpoint}")
    String dynamoDbEndpoint;

    @Inject
    DpsHeaders headers;

    private DynamoDBQueryHelper queryHelper;

    @PostConstruct
    public void init() {
        queryHelper = new DynamoDBQueryHelper(dynamoDbEndpoint, dynamoDbRegion, tablePrefix);
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
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
            queryPageResult = queryHelper.queryByGSI(SchemaDoc.class, queryObject, numRecords, cursor);

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
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor) {
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

        QueryPageResult<RecordMetadataDoc> scanPageResults;
        try {
            scanPageResults = queryHelper.queryPage(RecordMetadataDoc.class, recordMetadataKey, "Status", "active", numRecords, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(), e);
        }
        dqr.setCursor(scanPageResults.cursor); // set the cursor for the next page, if applicable
        scanPageResults.results.forEach(schemaDoc -> ids.add(schemaDoc.getId())); // extract the Kinds from the SchemaDocs

        // Sort the IDs alphabetically and set the results
        Collections.sort(ids);
        dqr.setResults(ids);
        return dqr;
    }

    private Map<String, AttributeValue> deserializeCursor(String cursor) {
        // The cursor string needs to be deserialized into a DynamoDB-compatible hash map
        Map<String, AttributeValue> cursorMap = new HashMap<>(); // initialize Map
        try {
            cursor = URLDecoder.decode(cursor, StandardCharsets.UTF_8.toString()); // decode the URL-encoded cursor string
            cursor = cursor.substring(1, cursor.length() - 1); // drop the opening and closing curly braces ({})
            String[] MapPairs = cursor.split(", "); // split the remaining string into an array of key/value pairs
            for (String pair : MapPairs) {
                String[] keyValue = pair.split("="); // split the pair on the equals sign (=)
                String[] attributeValueSplit = keyValue[1].split("(: )|(,})"); // the attribute values are serialized in a format like '{S: active,}', and we just want the value
                AttributeValue pairAttributeValue = new AttributeValue(attributeValueSplit[1]);
                cursorMap.put(keyValue[0], pairAttributeValue); // append the pair to the Map
            }
        } catch (Exception e) {
            throw new InvalidCursorException(e.getMessage());
        }
        return cursorMap;
    }
}