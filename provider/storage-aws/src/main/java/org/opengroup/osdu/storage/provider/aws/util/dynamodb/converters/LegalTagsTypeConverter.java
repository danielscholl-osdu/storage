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
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;


import javax.inject.Inject;
import java.io.IOException;
import java.util.Set;

// Converts the complex type of an array of legaltag strings to a string and vice-versa.
public class LegalTagsTypeConverter implements DynamoDBTypeConverter<String, Set<String>> {

    @Inject
    private JaxRsDpsLog logger;

    @Override
    // Converts an array of legaltag strings to a JSON string for DynamoDB
    public String convert(Set<String> legaltags) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(legaltags);
        } catch (JsonProcessingException e) {
            logger.error(String.format("There was an error converting the schema to a JSON string. %s", e.getMessage()));
        }
        return null;
    }

    @Override
    // Converts a JSON string of an array of legaltag strings to a list of legaltag strings
    public Set<String> unconvert(String legaltagsString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(legaltagsString, new TypeReference<Set<String>>(){});
        } catch (JsonParseException e) {
            logger.error(String.format("There was an error parsing the legaltags JSON string. %s", e.getMessage()));
        } catch (JsonMappingException e) {
            logger.error(String.format("There was an error mapping the legaltags JSON string. %s", e.getMessage()));
        } catch (IOException e) {
            logger.error(String.format("There was an IO exception while mapping the legaltags objects. %s", e.getMessage()));
        } catch (Exception e) {
            logger.error(String.format("There was an unknown exception legaltags the schema. %s", e.getMessage()));
        }
        return null;
    }
}
