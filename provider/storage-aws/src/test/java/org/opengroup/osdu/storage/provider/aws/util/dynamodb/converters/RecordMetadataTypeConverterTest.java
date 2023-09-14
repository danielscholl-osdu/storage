package org.opengroup.osdu.storage.provider.aws.util.dynamodb.converters;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RecordMetadataTypeConverterTest {
    @InjectMocks    
    private RecordMetadataTypeConverter converter = new RecordMetadataTypeConverter();

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RecordMetadata recordMetadata;

    private String jsonString = "{\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null,\"kind\":\"unit-test\",\"id\":\"unit-test\",\"version\":1,\"dataPartition\":\"unit-test\",\"acl\":null,\"legal\":null,\"status\":null}";

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    @Test
    void convert_shouldReturnJsonString_whenListIsValid() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(recordMetadata)).thenReturn(jsonString);

        String result = converter.convert(recordMetadata);

        assertEquals(jsonString, result);
    }

    @Test
    void convert_shouldNotThrowNullPointerException_whenObjectMapperIsNotInjected() {
        assertDoesNotThrow(() -> {
            String result = converter.convert(recordMetadata);
        });
    }
    

    @Test
    void convert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(recordMetadata)).thenThrow(JsonProcessingException.class);

        String result = converter.convert(recordMetadata);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldReturnObject_whenJsonStringIsValid() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenReturn(recordMetadata);

        Object result = converter.unconvert(jsonString);

        assertEquals(recordMetadata, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonParseExceptionOccurs() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonParseException.class);

        Object result = converter.unconvert(jsonString);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenUncheckedIOExceptionOccurs() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(UncheckedIOException.class);

        Object result = converter.unconvert(jsonString);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenJsonMappingExceptionOccurs() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(JsonMappingException.class);

        Object result = converter.unconvert(jsonString);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenIOExceptionOccurs() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(new UncheckedIOException(new IOException("Test IOException")));

        Object result = converter.unconvert(jsonString);

        assertEquals(null, result);
    }

    @Test
    void unconvert_shouldLogErrorAndReturnNull_whenExceptionOccurs() throws JsonParseException, JsonMappingException, IOException {
        when(objectMapper.readValue(eq(jsonString), any(TypeReference.class))).thenThrow(RuntimeException.class);

        Object result = converter.unconvert(jsonString);

        assertEquals(null, result);
    }

}
