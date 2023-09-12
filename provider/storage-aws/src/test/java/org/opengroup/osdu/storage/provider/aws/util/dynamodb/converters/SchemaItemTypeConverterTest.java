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
import org.opengroup.osdu.core.common.model.storage.SchemaItem;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SchemaItemTypeConverterTest {
    @InjectMocks
    private SchemaItemTypeConverter converter = new SchemaItemTypeConverter();

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    private List<SchemaItem> schemaItems = new ArrayList<SchemaItem>();

    private String jsonString = "[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":[{\"name\":\"test\",\"type\":\"test\",\"required\":true,\"description\":\"test\",\"schemaItems\":null}]}]}]}]}]}]}]}]}]";

    @BeforeEach
    void setUp() {
        openMocks(this);
        schemaItems.add(new SchemaItem());
    }

    @Test
    void convert_shouldReturnJsonString_whenListIsValid() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(schemaItems)).thenReturn(jsonString);

        String result = converter.convert(schemaItems);

        assertEquals(jsonString, result);
    }

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(schemaItems)).thenThrow(new JsonProcessingException("test"){});

        String result = converter.convert(schemaItems);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldReturnSchemaItems_whenJsonStringIsValid() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(schemaItems);

        List<SchemaItem> result = converter.unconvert(jsonString);

        assertEquals(schemaItems, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenIOExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));

        List<SchemaItem> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonParseExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);

        List<SchemaItem> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonMappingExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);

        List<SchemaItem> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);

        List<SchemaItem> result = converter.unconvert(jsonString);
        verify(logger).error(anyString());
        assertEquals(null, result);
    }

}
