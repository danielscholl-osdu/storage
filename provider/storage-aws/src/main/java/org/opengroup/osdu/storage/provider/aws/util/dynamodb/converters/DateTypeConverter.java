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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.util.Date;

// Converts Date Object to a string and vice-versa.
public class DateTypeConverter implements AttributeConverter<Date> {

    @Inject
    private JaxRsDpsLog logger;

    @Inject
    private ObjectMapper objectMapper;
    
    {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
    }

    // Converts Date to a JSON string (In form of AttributeValue) for DynamoDB
    @Override
    public AttributeValue transformFrom(Date date) {
        try {
            return AttributeValue.fromS(objectMapper.writeValueAsString(date));
        } catch (JsonProcessingException e) {
            logger.error(String.format("There was an error converting the date to a JSON string. %s", e.getMessage()));
        }
        return null;
    }

    // Converts a JSON string (In form of AttributeValue) into a Date object
    @Override
    public Date transformTo(AttributeValue dateString) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return objectMapper.readValue(dateString.s(), new TypeReference<Date>(){});
        } catch (JsonParseException e) {
            logger.error(String.format("There was an error parsing the date JSON string. %s", e.getMessage()));
        } catch (JsonMappingException e) {
            logger.error(String.format("There was an error mapping the date JSON string. %s", e.getMessage()));
        } catch (IOException e) {
            logger.error(String.format("There was an IO exception while mapping the date objects. %s", e.getMessage()));
        } catch (Exception e) {
            logger.error(String.format("There was an unknown exception converting the date. %s", e.getMessage()));
        }
        return null;
    }

    @Override
    public EnhancedType<Date> type() {
        return EnhancedType.of(Date.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
