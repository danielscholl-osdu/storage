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

package org.opengroup.osdu.storage.provider.aws;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import org.opengroup.osdu.core.aws.dynamodb.QueryPageResult;
import org.opengroup.osdu.core.aws.exceptions.InvalidCursorException;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.SchemaDoc;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Repository;

import java.io.UnsupportedEncodingException;
import javax.annotation.PostConstruct;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {

    @Value("${aws.dynamodb.table.prefix}")
    String tablePrefix;

    @Value("${aws.dynamodb.region}")
    String dynamoDbRegion;

    @Value("${aws.dynamodb.endpoint}")
    String dynamoDbEndpoint;

    private DynamoDBQueryHelper queryHelper;

    @PostConstruct
    public void init(){
        queryHelper = new DynamoDBQueryHelper(dynamoDbEndpoint, dynamoDbRegion, tablePrefix);
    }

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
        // Set the page size, or use the default constant
        int numRecords = PAGE_SIZE;
        if (limit != null) {
            numRecords = limit > 0 ? limit : PAGE_SIZE;
        }

        DatastoreQueryResult dqr = new DatastoreQueryResult();
        List<String> kinds = new ArrayList<>();

        QueryPageResult<SchemaDoc> scanPageResults;
        try {
            scanPageResults = queryHelper.scanPage(SchemaDoc.class, numRecords, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(),e);
        }

        dqr.setCursor(scanPageResults.cursor); // set the cursor for the next page, if applicable
        scanPageResults.results.forEach(schemaDoc -> kinds.add(schemaDoc.getKind())); // extract the Kinds from the SchemaDocs

        // Sort the Kinds alphabetically and set the results
        Collections.sort(kinds);
        dqr.setResults(kinds);
        return dqr;
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor)
    {
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
            scanPageResults = queryHelper.queryPage(RecordMetadataDoc.class, recordMetadataKey, "Status","active", numRecords, cursor);
        } catch (UnsupportedEncodingException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error parsing results",
                    e.getMessage(),e);
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
        } catch(Exception e) {
            throw new InvalidCursorException(e.getMessage());
        }
        return cursorMap;
    }
}