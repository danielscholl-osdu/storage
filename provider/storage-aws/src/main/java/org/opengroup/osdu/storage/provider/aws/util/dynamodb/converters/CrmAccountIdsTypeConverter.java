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

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;

import jakarta.inject.Inject;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.util.List;

// Converts the complex type of an array of CRM account ID strings to a string and vice-versa.
public class CrmAccountIdsTypeConverter implements AttributeConverter<List<String>> {

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private ObjectMapper objectMapper;

    {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
    }

    // Converts an array of CRM account ID strings to a JSON string (In form of AttributeValue) for DynamoDB
    @Override
    public AttributeValue transformFrom(List<String> crmAccountIds) {
        try {
            return AttributeValue.fromS(objectMapper.writeValueAsString(crmAccountIds));
        } catch (JsonProcessingException e) {
            logger.error(String.format("There was an error converting the schema to a JSON string. %s", e.getMessage()));
        }
        return null;
    }

    // Converts a JSON string (In form of AttributeValue) of an array of CRM account ID strings to a list of CRM account ID strings
    @Override
    public List<String> transformTo(AttributeValue crmAccountIdsString) {
        try {
            return objectMapper.readValue(crmAccountIdsString.s(), new TypeReference<List<String>>(){});
        } catch (JsonParseException e) {
            logger.error(String.format("There was an error parsing the crmAccountIds JSON string. %s", e.getMessage()));
        } catch (JsonMappingException e) {
            logger.error(String.format("There was an error mapping the crmAccountIds JSON string. %s", e.getMessage()));
        } catch (IOException e) {
            logger.error(String.format("There was an IO exception while mapping the crmAccountIds objects. %s", e.getMessage()));
        } catch (Exception e) {
            logger.error(String.format("There was an unknown exception crmAccountIds the schema. %s", e.getMessage()));
        }
        return null;
    }

    @Override
    public EnhancedType<List<String>> type() {
        return EnhancedType.listOf(String.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
