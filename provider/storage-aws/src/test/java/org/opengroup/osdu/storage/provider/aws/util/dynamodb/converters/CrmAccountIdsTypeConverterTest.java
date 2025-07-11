// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class CrmAccountIdsTypeConverterTest {
    @InjectMocks
    private CrmAccountIdsTypeConverter converter = new CrmAccountIdsTypeConverter();

    @Mock
    private JaxRsDpsLog logger;

    @Mock 
    private ObjectMapper objectMapper;

    private List<String> accountIds = new ArrayList<>();

    private String jsonString = "[\"id1\",\"id2\"]";

    @BeforeEach
    void setUp() {
        openMocks(this);
        accountIds.add("id1");
        accountIds.add("id2");
    }

    @Test
    void convert_shouldReturnJsonString_whenListIsValid() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(accountIds)).thenReturn(jsonString);

        AttributeValue result = converter.transformFrom(accountIds);

        assertEquals(jsonString, result.s());
    }

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(accountIds)).thenThrow(JsonProcessingException.class);
        AttributeValue result = converter.transformFrom(accountIds);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldReturnList_whenJsonStringIsValid() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(accountIds);


        List<String> result = converter.transformTo(AttributeValue.builder().s(jsonString).build());
        assertEquals(accountIds, result);
    }


    @Test
    void unconvert_shouldLogError_whenJsonParseExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);
        converter.transformTo(AttributeValue.builder().s(jsonString).build());
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenJsonMappingExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);
        converter.transformTo(AttributeValue.builder().s(jsonString).build());
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);
        converter.transformTo(AttributeValue.builder().s(jsonString).build());
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenIOExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));
        converter.transformTo(AttributeValue.builder().s(jsonString).build());
        verify(logger).error(anyString());
    }


}
