/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaIdentity;
import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaInfo;
import org.opengroup.osdu.storage.provider.aws.model.schema.SchemaInfoResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AwsSchemaServiceImplTest {

    private static final String SCHEMA_API_URL = "https://schema-api.example.com";
    private static final String AUTH_TOKEN = "Bearer test-token";
    private static final String PARTITION_ID = "test-partition";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private AwsSchemaServiceImpl schemaService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(schemaService, "schemaApiUrl", SCHEMA_API_URL);
        
        when(headers.getAuthorization()).thenReturn(AUTH_TOKEN);
        when(headers.getPartitionId()).thenReturn(PARTITION_ID);
    }

    @Test
    void getAllSchemas_SinglePage_Success() {
        // Arrange
        SchemaInfoResponse mockResponse = new SchemaInfoResponse();
        List<SchemaInfo> schemaInfos = createMockSchemaInfos(5);
        mockResponse.setSchemaInfos(schemaInfos);
        mockResponse.setCount(5);
        mockResponse.setTotalCount(5);
        mockResponse.setOffset(0);

        ResponseEntity<SchemaInfoResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        // Use doReturn().when() pattern with any() matchers to avoid strict stubbing issues
        doReturn(responseEntity).when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act
        SchemaInfoResponse result = schemaService.getAllSchemas();

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getCount());
        assertEquals(5, result.getTotalCount());
        assertEquals(5, result.getSchemaInfos().size());
        verify(logger).info("Successfully retrieved all {} schemas from Schema Service", "5");
    }

    @Test
    void getAllSchemas_MultiplePages_Success() {
        // Arrange
        // First page response
        SchemaInfoResponse firstPageResponse = new SchemaInfoResponse();
        List<SchemaInfo> firstPageSchemas = createMockSchemaInfos(3);
        firstPageResponse.setSchemaInfos(firstPageSchemas);
        firstPageResponse.setCount(3);
        firstPageResponse.setTotalCount(5); // Total of 5 schemas across all pages
        firstPageResponse.setOffset(0);

        ResponseEntity<SchemaInfoResponse> firstPageEntity = new ResponseEntity<>(firstPageResponse, HttpStatus.OK);
        
        // Second page response
        SchemaInfoResponse secondPageResponse = new SchemaInfoResponse();
        List<SchemaInfo> secondPageSchemas = createMockSchemaInfos(2, 3); // Start from ID 3
        secondPageResponse.setSchemaInfos(secondPageSchemas);
        secondPageResponse.setCount(2);
        secondPageResponse.setTotalCount(5);
        secondPageResponse.setOffset(3);

        ResponseEntity<SchemaInfoResponse> secondPageEntity = new ResponseEntity<>(secondPageResponse, HttpStatus.OK);
        
        // Empty third page response to signal end
        SchemaInfoResponse emptyPageResponse = new SchemaInfoResponse();
        emptyPageResponse.setSchemaInfos(Collections.emptyList());
        emptyPageResponse.setCount(0);
        emptyPageResponse.setTotalCount(5);
        emptyPageResponse.setOffset(5);

        ResponseEntity<SchemaInfoResponse> emptyPageEntity = new ResponseEntity<>(emptyPageResponse, HttpStatus.OK);

        // Use doReturn().when() with argument matchers for each call
        // First call - offset=0
        doReturn(firstPageEntity)
            .doReturn(secondPageEntity)
            .doReturn(emptyPageEntity)
            .when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act
        SchemaInfoResponse result = schemaService.getAllSchemas();

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getCount());
        assertEquals(5, result.getTotalCount());
        assertEquals(5, result.getSchemaInfos().size());
        
        // Verify logging with exact message strings
        verify(logger).info("Retrieved 3 schemas, total so far: 3, total available: 5");
        verify(logger).info("Retrieved 2 schemas, total so far: 5, total available: 5");
        verify(logger).info("Successfully retrieved all {} schemas from Schema Service", "5");
    }

    @Test
    void getAllSchemas_EmptyResponse_Success() {
        // Arrange
        SchemaInfoResponse mockResponse = new SchemaInfoResponse();
        mockResponse.setSchemaInfos(Collections.emptyList());
        mockResponse.setCount(0);
        mockResponse.setTotalCount(0);
        mockResponse.setOffset(0);

        ResponseEntity<SchemaInfoResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        // Use doReturn().when() pattern with any() matchers
        doReturn(responseEntity).when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act
        SchemaInfoResponse result = schemaService.getAllSchemas();

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getCount());
        assertEquals(0, result.getTotalCount());
        assertTrue(result.getSchemaInfos().isEmpty());
        verify(logger).info("Successfully retrieved all {} schemas from Schema Service", "0");
    }

    @Test
    void getAllSchemas_RestClientException_ThrowsAppException() {
        // Arrange - Use doThrow().when() pattern
        doThrow(new RestClientException("Connection refused"))
            .when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> schemaService.getAllSchemas());
        assertEquals(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
        assertEquals("Error retrieving schemas", exception.getError().getReason());
        assertTrue(exception.getError().getMessage().contains("Connection refused"));
        
        verify(logger).error("Error retrieving schemas from Schema Service: {}", "Connection refused");
    }

    @Test
    void getAllKinds_Success() {
        // Arrange
        SchemaInfoResponse mockResponse = new SchemaInfoResponse();
        
        // Create schema infos with some duplicate kinds
        SchemaInfo schema1 = createSchemaInfo("kind1", "1");
        SchemaInfo schema2 = createSchemaInfo("kind2", "2");
        SchemaInfo schema3 = createSchemaInfo("kind1", "3"); // Duplicate kind
        SchemaInfo schema4 = createSchemaInfo("kind3", "4");
        
        mockResponse.setSchemaInfos(Arrays.asList(schema1, schema2, schema3, schema4));
        mockResponse.setCount(4);
        mockResponse.setTotalCount(4);
        mockResponse.setOffset(0);

        ResponseEntity<SchemaInfoResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        // Use doReturn().when() pattern with any() matchers
        doReturn(responseEntity).when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act
        List<String> result = schemaService.getAllKinds();

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size()); // Should have 3 unique kinds
        assertTrue(result.contains("kind1"));
        assertTrue(result.contains("kind2"));
        assertTrue(result.contains("kind3"));
        
        verify(logger).info("Retrieved {} unique kinds from Schema Service", "3");
    }

    @Test
    void getAllKinds_NoSchemas_ThrowsAppException() {
        // Arrange - Return null response body
        ResponseEntity<SchemaInfoResponse> responseEntity = new ResponseEntity<>(null, HttpStatus.OK);
        
        // Use doReturn().when() pattern with any() matchers
        doReturn(responseEntity).when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> schemaService.getAllKinds());
        assertEquals(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
        assertEquals("No schemas available", exception.getError().getReason());
        
        verify(logger).error("No schemas returned from Schema Service");
    }

    @Test
    void getAllKinds_EmptySchemaList_ThrowsAppException() {
        // Arrange
        SchemaInfoResponse mockResponse = new SchemaInfoResponse();
        mockResponse.setSchemaInfos(Collections.emptyList()); // Empty schema list
        
        ResponseEntity<SchemaInfoResponse> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        
        // Use doReturn().when() pattern with any() matchers
        doReturn(responseEntity).when(restTemplate).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SchemaInfoResponse.class));

        // Act & Assert
        AppException exception = assertThrows(AppException.class, () -> schemaService.getAllKinds());
        assertEquals(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, exception.getError().getCode());
        assertEquals("No schemas available", exception.getError().getReason());
        assertEquals("Schema Service returned no schemas", exception.getError().getMessage());
        
        verify(logger).error("No schemas returned from Schema Service");
    }

    // Helper methods
    private List<SchemaInfo> createMockSchemaInfos(int count) {
        return createMockSchemaInfos(count, 0);
    }
    
    private List<SchemaInfo> createMockSchemaInfos(int count, int startId) {
        List<SchemaInfo> schemaInfos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SchemaInfo schemaInfo = new SchemaInfo();
            SchemaIdentity identity = new SchemaIdentity();
            identity.setId("kind" + (i + startId));
            schemaInfo.setSchemaIdentity(identity);
            schemaInfos.add(schemaInfo);
        }
        return schemaInfos;
    }
    
    private SchemaInfo createSchemaInfo(String kind, String id) {
        SchemaInfo schemaInfo = new SchemaInfo();
        SchemaIdentity identity = new SchemaIdentity();
        identity.setId(kind);
        schemaInfo.setSchemaIdentity(identity);
        return schemaInfo;
    }
}
