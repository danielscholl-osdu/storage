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

package org.opengroup.osdu.storage.provider.aws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.GsiQueryRequest;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.QueryPageResult;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.service.AwsSchemaServiceImpl;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


class QueryRepositoryImplTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private QueryRepositoryImpl repo;

    @Mock
    private DynamoDBQueryHelper queryHelper;

    @Mock
    private DynamoDBQueryHelperFactory queryHelperFactory;

    @Mock
    private DpsHeaders dpsHeaders;
    
    @Mock
    private JaxRsDpsLog logger;
    
    @Mock
    private AwsSchemaServiceImpl schemaService;

    @BeforeEach
    void setUp() {
        openMocks(this);
        Mockito.when(queryHelperFactory.createQueryHelper(any(DpsHeaders.class), any(), any()))
            .thenReturn(queryHelper);
        
        // Manually set the schemaService field in the repo
        repo.schemaService = schemaService;
    }
    
    @Test
    void getAllRecordIdAndKindThrowsException() {
        // Arrange
        String cursor = "abc123";
        Integer limit = 50;

        // Act & Assert This function is disabled
        assertThrows(UnsupportedOperationException.class, () -> {
            repo.getAllRecordIdAndKind(limit, cursor);
        });
    }
    
    @Test
    void getAllRecordIdsFromKindWithRecordId() {
        // Arrange
        String kind = "tenant:source:type:1.0.0";
        String cursor = null;
        String recordId1 = "tenant:source:type:1.0.0.1212";
        String recordId2 = "tenant:source:type:1.0.0.3434";
        String partitionId = "tenant";
        
        // Create test data
        RecordMetadataDoc doc1 = new RecordMetadataDoc();
        doc1.setId(recordId1);
        doc1.setKind(kind);
        doc1.setStatus("active");
        
        RecordMetadataDoc doc2 = new RecordMetadataDoc();
        doc2.setId(recordId2);
        doc2.setKind(kind);
        doc2.setStatus("active");
        
        List<RecordMetadataDoc> docList = Arrays.asList(doc1, doc2);
        QueryPageResult<RecordMetadataDoc> queryResult = new QueryPageResult<>(docList, null, null);
        
        // Mock the query helper and headers
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenReturn(queryResult);
        
        // Act
        RecordInfoQueryResult<RecordId> result = repo.getAllRecordIdsFromKind(50, cursor, kind);
        
        // Assert
        assertEquals(cursor, result.getCursor());
        assertEquals(2, result.getResults().size());
        
        RecordId record1 = result.getResults().get(0);
        assertEquals(recordId1, record1.getId());
        
        RecordId record2 = result.getResults().get(1);
        assertEquals(recordId2, record2.getId());
        
        verify(queryHelper, times(1)).queryByGSI(any(GsiQueryRequest.class));
    }
    
    @Test
    void getAllRecordIdsFromKindWithRecordIdThrowsException() {
        // Arrange
        String kind = "tenant:source:type:1.0.0";
        String cursor = null;
        Integer limit = 50;
        
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenThrow(IllegalArgumentException.class);

        
        // Act & Assert
        assertThrows(AppException.class, () -> {
            repo.getAllRecordIdsFromKind(limit, cursor, kind);
        });
    }
    
    @Test
    void getActiveRecordsCount() {
        // Arrange
        String partitionId = "tenant";
        String kind1 = "tenant:source:type1:1.0.0";
        String kind2 = "tenant:source:type2:1.0.0";
        List<String> kinds = Arrays.asList(kind1, kind2);
        
        // Create test data for kind1
        RecordMetadataDoc doc1 = new RecordMetadataDoc();
        doc1.setId("tenant:source:type1:1.0.0.1212");
        doc1.setKind(kind1);
        doc1.setStatus("active");
        
        RecordMetadataDoc doc2 = new RecordMetadataDoc();
        doc2.setId("tenant:source:type1:1.0.0.3434");
        doc2.setKind(kind1);
        doc2.setStatus("active");
        
        List<RecordMetadataDoc> docList1 = Arrays.asList(doc1, doc2);
        QueryPageResult<RecordMetadataDoc> queryResult1 = new QueryPageResult<>(docList1, null, null);
        
        // Create test data for kind2
        RecordMetadataDoc doc3 = new RecordMetadataDoc();
        doc3.setId("tenant:source:type2:1.0.0.5656");
        doc3.setKind(kind2);
        doc3.setStatus("active");
        
        List<RecordMetadataDoc> docList2 = Arrays.asList(doc3);
        QueryPageResult<RecordMetadataDoc> queryResult2 = new QueryPageResult<>(docList2, null, null);
        
        // Mock the schema service and query helper
        when(schemaService.getAllKinds()).thenReturn(kinds);
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);
        
        // Mock for returning result1 when first called then return result2 when 2nd call
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenReturn(queryResult1).thenReturn(queryResult2);

        
        // Act
        HashMap<String, Long> result = repo.getActiveRecordsCount();
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(2), result.get(kind1));
        assertEquals(Long.valueOf(1), result.get(kind2));
    }
    
    @Test
    void getActiveRecordsCountWithPagination() {
        // Arrange
        String partitionId = "tenant";
        String kind = "tenant:source:type:1.0.0";
        List<String> kinds = Arrays.asList(kind);
        String cursor = "{}";
        
        // Create test data for first page
        RecordMetadataDoc doc1 = new RecordMetadataDoc();
        doc1.setId("tenant:source:type:1.0.0.1212");
        doc1.setKind(kind);
        doc1.setStatus("active");
        
        RecordMetadataDoc doc2 = new RecordMetadataDoc();
        doc2.setId("tenant:source:type:1.0.0.3434");
        doc2.setKind(kind);
        doc2.setStatus("active");
        
        List<RecordMetadataDoc> docList1 = Arrays.asList(doc1, doc2);
        QueryPageResult<RecordMetadataDoc> queryResult1 = new QueryPageResult<>(docList1, null, cursor);
        
        // Create test data for second page
        RecordMetadataDoc doc3 = new RecordMetadataDoc();
        doc3.setId("tenant:source:type:1.0.0.5656");
        doc3.setKind(kind);
        doc3.setStatus("active");
        
        List<RecordMetadataDoc> docList2 = Arrays.asList(doc3);
        QueryPageResult<RecordMetadataDoc> queryResult2 = new QueryPageResult<>(docList2, null, null);
        
        // Mock the schema service and query helper
        when(schemaService.getAllKinds()).thenReturn(kinds);
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);
        
        // Mock for query
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenReturn(queryResult1, queryResult2);
        
        // Act
        HashMap<String, Long> result = repo.getActiveRecordsCount();
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(Long.valueOf(3), result.get(kind));
        
        // Verify that both pages were queried
        verify(queryHelper, times(1)).queryByGSI(Mockito.argThat(arg -> arg != null && arg.getRequest().exclusiveStartKey() == null));
        
        verify(queryHelper, times(1)).queryByGSI(Mockito.argThat(arg -> arg != null && arg.getRequest().exclusiveStartKey() != null));
    }
    
    @Test
    void getActiveRecordsCountWithException() {
        // Mock the schema service to throw an exception
        when(schemaService.getAllKinds()).thenThrow(new RuntimeException("Test exception"));
        
        // Act & Assert
        assertThrows(AppException.class, () -> {
            repo.getActiveRecordsCount();
        });
    }
    
    @Test
    void getActiveRecordsCountForKinds() {
        // Arrange
        String partitionId = "tenant";
        String kind1 = "tenant:source:type1:1.0.0";
        String kind2 = "tenant:source:type2:1.0.0";
        List<String> kinds = Arrays.asList(kind1, kind2);
        
        // Create test data for kind1
        RecordMetadataDoc doc1 = new RecordMetadataDoc();
        doc1.setId("tenant:source:type1:1.0.0.1212");
        doc1.setKind(kind1);
        doc1.setStatus("active");
        
        RecordMetadataDoc doc2 = new RecordMetadataDoc();
        doc2.setId("tenant:source:type1:1.0.0.3434");
        doc2.setKind(kind1);
        doc2.setStatus("active");
        
        List<RecordMetadataDoc> docList1 = Arrays.asList(doc1, doc2);
        QueryPageResult<RecordMetadataDoc> queryResult1 = new QueryPageResult<>(docList1, null, null);
        
        // Create test data for kind2
        RecordMetadataDoc doc3 = new RecordMetadataDoc();
        doc3.setId("tenant:source:type2:1.0.0.5656");
        doc3.setKind(kind2);
        doc3.setStatus("active");
        
        List<RecordMetadataDoc> docList2 = Arrays.asList(doc3);
        QueryPageResult<RecordMetadataDoc> queryResult2 = new QueryPageResult<>(docList2, null, null);
        
        // Mock the query helper
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);

        // Return result1 when 1st called, return result2 when 2nd called
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenReturn(queryResult1, queryResult2);
        
        // Act
        Map<String, Long> result = repo.getActiveRecordsCountForKinds(kinds);
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(2), result.get(kind1));
        assertEquals(Long.valueOf(1), result.get(kind2));
    }
    
    @Test
    void getActiveRecordsCountForKindsWithException() {
        // Arrange
        String partitionId = "tenant";
        String kind1 = "tenant:source:type1:1.0.0";
        String kind2 = "tenant:source:type2:1.0.0";
        List<String> kinds = Arrays.asList(kind1, kind2);
        
        when(dpsHeaders.getPartitionId()).thenReturn(partitionId);

        
        // Create test data for kind2
        RecordMetadataDoc doc3 = new RecordMetadataDoc();
        doc3.setId("tenant:source:type2:1.0.0.5656");
        doc3.setKind(kind2);
        doc3.setStatus("active");
        
        List<RecordMetadataDoc> docList2 = Arrays.asList(doc3);
        QueryPageResult<RecordMetadataDoc> queryResult2 = new QueryPageResult<>(docList2, null, null);

        // Mock to throw exception when 1st call, then return value at 2nd call.
        when(queryHelper.queryByGSI(any(GsiQueryRequest.class)))
                .thenThrow(DynamoDbException.class).thenReturn(queryResult2);

        // Act
        Map<String, Long> result = repo.getActiveRecordsCountForKinds(kinds);
        
        // Assert
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(0), result.get(kind1)); // Should be 0 due to exception
        assertEquals(Long.valueOf(1), result.get(kind2));
        
        // Verify that logger.error was called for the exception
        verify(logger, times(1)).error(Mockito.contains("Error counting records for kind " + kind1), any(Exception.class));
    }
}
