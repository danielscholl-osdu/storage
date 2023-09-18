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
import java.util.HashMap;
import java.util.Map;

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

class SchemaExtTypeConverterTest {
    @InjectMocks
    private SchemaExtTypeConverter converter = new SchemaExtTypeConverter();

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    private Map<String, Object> ext = new HashMap<String, Object>();

    private String jsonString = "{\"test\":\"test\"}";

    @BeforeEach
    void setUp() {
        openMocks(this);
        ext.put("test", "test");
    }

    @Test
    void convert_shouldReturnJsonString_whenListIsValid() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(ext)).thenReturn(jsonString);

        String result = converter.convert(ext);

        assertEquals(jsonString, result);
    }

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(ext)).thenThrow(new JsonProcessingException("Test exception") {});

        String result = converter.convert(ext);

        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldReturnSchemaItemArray_whenJsonStringIsValid() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(ext);

        Map<String, Object> result = converter.unconvert(jsonString);
        assertEquals(ext, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenIOExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));

        Map<String, Object> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonParseExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);

        Map<String, Object> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonMappingExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);

        Map<String, Object> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);

        Map<String, Object> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }
}
