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
import java.util.HashSet;
import java.util.Set;

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

class LegalTagsTypeConverterTest {
    @InjectMocks
    private LegalTagsTypeConverter converter = new LegalTagsTypeConverter();

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    private Set<String> legalTags = new HashSet<String>();

    private String jsonString = "[\"test-tag1\",\"test-tag2\"]";

    @BeforeEach
    void setUp() {
        openMocks(this);
        legalTags.add("test-tag1");
        legalTags.add("test-tag2");
    }

    @Test
    void convert_shouldReturnJsonString_whenListIsValid() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(legalTags)).thenReturn(jsonString);

        String result = converter.convert(legalTags);

        assertEquals(jsonString, result);
    }

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(legalTags)).thenThrow(JsonProcessingException.class);

        String result = converter.convert(legalTags);

        assertEquals(null, result);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldReturnList_whenJsonStringIsValid() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(legalTags);

        Set<String> result = converter.unconvert(jsonString);

        assertEquals(legalTags, result);
    }

    @Test
    void unconvert_shouldLogError_whenJsonParseExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);

        Set<String> result = converter.unconvert(jsonString);

        assertEquals(null, result);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenJsonMappingExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);

        Set<String> result = converter.unconvert(jsonString);

        assertEquals(null, result);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);

        Set<String> result = converter.unconvert(jsonString);

        assertEquals(null, result);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenIOExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));

        Set<String> result = converter.unconvert(jsonString);

        assertEquals(null, result);
        verify(logger).error(anyString());
    }

}
