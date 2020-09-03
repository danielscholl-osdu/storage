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

package org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;


import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

// Converts the complex type of a SchemaItem array to a string and vice-versa.
public class SchemaItemTypeConverter implements DynamoDBTypeConverter<String, List<SchemaItem>> {

    @Inject
    private JaxRsDpsLog logger;

    @Override
    // Converts a list of SchemaItems to a JSON string for DynamoDB
    public String convert(List<SchemaItem> schemaItems) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(schemaItems);
        } catch (JsonProcessingException e) {
            logger.error(String.format("There was an error converting the schema to a JSON string. %s", e.getMessage()));
        }
        return null;
    }

    @Override
    // Converts a JSON string of an array of SchemaItems to a list of SchemaItem objects
    public List<SchemaItem> unconvert(String schemaItemsString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(schemaItemsString, new TypeReference<List<SchemaItem>>(){});
        } catch (JsonParseException e) {
            logger.error(String.format("There was an error parsing the schema JSON string. %s", e.getMessage()));
        } catch (JsonMappingException e) {
            logger.error(String.format("There was an error mapping the schema JSON string. %s", e.getMessage()));
        } catch (IOException e) {
            logger.error(String.format("There was an IO exception while mapping the schema objects. %s", e.getMessage()));
        } catch (Exception e) {
            logger.error(String.format("There was an unknown exception converting the schema. %s", e.getMessage()));
        }
        return null;
    }
}
