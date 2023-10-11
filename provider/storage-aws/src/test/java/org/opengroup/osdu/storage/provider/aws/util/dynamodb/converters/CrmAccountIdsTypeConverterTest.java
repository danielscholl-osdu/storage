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

        String result = converter.convert(accountIds);

        assertEquals(jsonString, result);
    }

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(accountIds)).thenThrow(JsonProcessingException.class);
        converter.convert(accountIds);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldReturnList_whenJsonStringIsValid() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(accountIds);


        List<String> result = converter.unconvert(jsonString);
        assertEquals(accountIds, result);
    }


    @Test
    void unconvert_shouldLogError_whenJsonParseExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);
        converter.unconvert(jsonString);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenJsonMappingExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);
        converter.unconvert(jsonString);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);
        converter.unconvert(jsonString);
        verify(logger).error(anyString());
    }

    @Test
    void unconvert_shouldLogError_whenIOExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));
        converter.unconvert(jsonString);
        verify(logger).error(anyString());
    }


}
