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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;


import javax.inject.Inject;
import java.io.IOException;

// Converts the complex type of RecordMetadata to a string and vice-versa.
public class RecordMetadataTypeConverter implements DynamoDBTypeConverter<String, RecordMetadata> {

    @Inject
    private JaxRsDpsLog logger;

    @Override
    // Converts RecordMetadata to a JSON string for DynamoDB
    public String convert(RecordMetadata recordMetadata) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(recordMetadata);
        } catch (JsonProcessingException e) {
            logger.error(String.format("There was an error converting the record metadata to a JSON string. %s", e.getMessage()));
        }
        return null;
    }

    @Override
    // Converts a JSON string of an array of RecordMetadata to a RecordMetadata object
    public RecordMetadata unconvert(String recordMetadataString) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return objectMapper.readValue(recordMetadataString, new TypeReference<RecordMetadata>(){});
        } catch (JsonParseException e) {
            logger.error(String.format("There was an error parsing the record metadata JSON string. %s", e.getMessage()));
        } catch (JsonMappingException e) {
            logger.error(String.format("There was an error mapping the record metadata JSON string. %s", e.getMessage()));
        } catch (IOException e) {
            logger.error(String.format("There was an IO exception while mapping the record metadata objects. %s", e.getMessage()));
        } catch (Exception e) {
            logger.error(String.format("There was an unknown exception converting the record metadata. %s", e.getMessage()));
        }
        return null;
    }
}
