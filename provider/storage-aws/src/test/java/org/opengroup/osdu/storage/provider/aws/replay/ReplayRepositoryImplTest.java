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

package org.opengroup.osdu.storage.provider.aws.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.QueryPageResult;
import org.opengroup.osdu.core.aws.v2.dynamodb.model.GsiQueryRequest;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayRepositoryImplTest {

    @Mock
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Mock
    private DynamoDBQueryHelper dynamoDBQueryHelper;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BatchWriteResult batchWriteResult;

    @InjectMocks
    private ReplayRepositoryImpl replayRepository;

    private static final String REPLAY_ID = "test-replay-id";
    private static final String KIND = "test-kind";
    private static final String PARTITION_ID = "test-partition";
    private static final String REPLAY_TABLE_PATH = "services/core/storage/ReplayStatusTable";

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(replayRepository, "replayStatusTableParameterRelativePath", REPLAY_TABLE_PATH);
        when(headers.getPartitionId()).thenReturn(PARTITION_ID);
        when(dynamoDBQueryHelperFactory.createQueryHelper(eq(headers), eq(REPLAY_TABLE_PATH), any())).thenReturn(dynamoDBQueryHelper);
    }

    @Test
    public void testGetReplayStatusByReplayId() throws UnsupportedEncodingException {
        // Prepare test data
        ReplayMetadataItem item1 = createReplayMetadataItem(KIND, REPLAY_ID);
        ReplayMetadataItem item2 = createReplayMetadataItem("another-kind", REPLAY_ID);
        List<ReplayMetadataItem> items = Arrays.asList(item1, item2);
        
        QueryPageResult<ReplayMetadataItem> queryResult = new QueryPageResult<>(items, null, null);
        
        // Mock behavior for queryByGSI
        when(dynamoDBQueryHelper.queryByGSI(any(GsiQueryRequest.class), anyBoolean())).thenReturn(queryResult);
        
        // Execute
        List<ReplayMetaDataDTO> result = replayRepository.getReplayStatusByReplayId(REPLAY_ID);
        
        // Verify
        assertEquals(2, result.size());
        verify(dynamoDBQueryHelper).queryByGSI(any(GsiQueryRequest.class), anyBoolean());
    }

    @Test
    public void testGetReplayStatusByReplayIdHandlesException() throws UnsupportedEncodingException {
        // Mock behavior to throw exception
        when(dynamoDBQueryHelper.queryByGSI(any(GsiQueryRequest.class), anyBoolean())).thenThrow(new IllegalArgumentException("Test exception"));
        
        // Execute
        List<ReplayMetaDataDTO> result = replayRepository.getReplayStatusByReplayId(REPLAY_ID);
        
        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(logger).error(contains("Error querying replay status"), any(IllegalArgumentException.class));
    }

    @Test
    public void testGetReplayStatusByKindAndReplayId() {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        
        // Mock behavior
        when(dynamoDBQueryHelper.getItem(KIND, REPLAY_ID)).thenReturn(Optional.of(item));
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        verify(dynamoDBQueryHelper).getItem(KIND, REPLAY_ID);
    }

    @Test
    public void testGetReplayStatusByKindAndReplayIdReturnsNullWhenNotFound() {
        // Mock behavior
        when(dynamoDBQueryHelper.getItem(KIND, REPLAY_ID)).thenReturn(Optional.empty());
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        
        // Verify
        assertNull(result);
        verify(dynamoDBQueryHelper).getItem(KIND, REPLAY_ID);
    }

    @Test
    public void testSave() {
        // Prepare test data
        ReplayMetaDataDTO dto = createReplayMetaDataDTO(KIND, REPLAY_ID);

        // Mock behavior
        doNothing().when(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
        
        // Execute
        ReplayMetaDataDTO result = replayRepository.save(dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        verify(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
    }

    @Test
    public void testConvertToItemHandlesFilterSerialization() throws JsonProcessingException {
        // Prepare test data
        ReplayMetaDataDTO dto = createReplayMetaDataDTO(KIND, REPLAY_ID);
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(KIND));
        dto.setFilter(filter);
        
        String serializedFilter = "{\"kinds\":[\"" + KIND + "\"]}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(filter)).thenReturn(serializedFilter);
        
        // Execute - we need to use reflection to test private method
        ReplayMetadataItem result = (ReplayMetadataItem) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertToItem", 
                dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(serializedFilter, result.getFilter());
        assertEquals(PARTITION_ID, result.getDataPartitionId());
        verify(objectMapper).writeValueAsString(filter);
    }

    @Test
    public void testConvertToDtoHandlesFilterDeserialization() throws JsonProcessingException {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        String serializedFilter = "{\"kinds\":[\"" + KIND + "\"]}";
        item.setFilter(serializedFilter);
        
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Collections.singletonList(KIND));
        
        // Mock behavior
        when(objectMapper.readValue(serializedFilter, ReplayFilter.class)).thenReturn(filter);
        
        // Execute - we need to use reflection to test private method
        ReplayMetaDataDTO result = (ReplayMetaDataDTO) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertToDTO", 
                item);
        
        // Verify
        assertNotNull(result);
        assertEquals(filter, result.getFilter());
        verify(objectMapper).readValue(serializedFilter, ReplayFilter.class);
    }
    
    // New tests for AWS-specific functionality
    
    @Test
    public void testGetAwsReplayStatusByKindAndReplayId() {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        item.setLastCursor("test-cursor");
        Date lastUpdated = new Date();
        item.setLastUpdatedAt(lastUpdated);
        
        // Mock behavior
        when(dynamoDBQueryHelper.getItem(KIND, REPLAY_ID)).thenReturn(Optional.of(item));
        
        // Execute
        AwsReplayMetaDataDTO result = replayRepository.getAwsReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        assertEquals("test-cursor", result.getLastCursor());
        assertEquals(lastUpdated, result.getLastUpdatedAt());
        verify(dynamoDBQueryHelper).getItem(KIND, REPLAY_ID);
    }

    @Test
    public void testSaveAwsReplayMetaData() {
        // Prepare test data
        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setId(KIND);
        dto.setReplayId(REPLAY_ID);
        dto.setKind(KIND);
        dto.setOperation("replay");
        dto.setTotalRecords(100L);
        dto.setProcessedRecords(50L);
        dto.setState("IN_PROGRESS");
        dto.setStartedAt(new Date());
        dto.setElapsedTime("00:10:00");
        dto.setLastCursor("test-cursor");
        Date lastUpdated = new Date();
        dto.setLastUpdatedAt(lastUpdated);

        // Mock behavior
        doNothing().when(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
        
        // Execute
        AwsReplayMetaDataDTO result = replayRepository.saveAwsReplayMetaData(dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        assertEquals("test-cursor", result.getLastCursor());
        assertEquals(lastUpdated, result.getLastUpdatedAt());
        verify(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
    }

    @Test
    public void testUpdateCursor() {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        String newCursor = "new-cursor";
        
        // Mock behavior
        when(dynamoDBQueryHelper.getItem(KIND, REPLAY_ID)).thenReturn(Optional.of(item));
        doNothing().when(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
        
        // Execute
        AwsReplayMetaDataDTO result = replayRepository.updateCursor(KIND, REPLAY_ID, newCursor);
        
        // Verify
        assertNotNull(result);
        assertEquals(newCursor, result.getLastCursor());
        assertNotNull(result.getLastUpdatedAt());
        verify(dynamoDBQueryHelper).getItem(KIND, REPLAY_ID);
        verify(dynamoDBQueryHelper).putItem(any(ReplayMetadataItem.class));
    }

    @Test
    public void testConvertToAwsDTO() {
        // Prepare test data
        ReplayMetadataItem item = createReplayMetadataItem(KIND, REPLAY_ID);
        String cursor = "test-cursor";
        Date lastUpdated = new Date();
        item.setLastCursor(cursor);
        item.setLastUpdatedAt(lastUpdated);
        
        // Execute - we need to use reflection to test private method
        AwsReplayMetaDataDTO result = (AwsReplayMetaDataDTO) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertToAwsDTO", 
                item);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        assertEquals(cursor, result.getLastCursor());
        assertEquals(lastUpdated, result.getLastUpdatedAt());
    }

    @Test
    public void testConvertAwsDtoToItem() {
        // Prepare test data
        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setId(KIND);
        dto.setReplayId(REPLAY_ID);
        dto.setKind(KIND);
        dto.setOperation("replay");
        dto.setTotalRecords(100L);
        dto.setProcessedRecords(50L);
        dto.setState("IN_PROGRESS");
        dto.setStartedAt(new Date());
        dto.setElapsedTime("00:10:00");
        
        String cursor = "test-cursor";
        Date lastUpdated = new Date();
        dto.setLastCursor(cursor);
        dto.setLastUpdatedAt(lastUpdated);
        
        // Execute - we need to use reflection to test private method
        ReplayMetadataItem result = (ReplayMetadataItem) ReflectionTestUtils.invokeMethod(
                replayRepository, 
                "convertAwsDtoToItem", 
                dto);
        
        // Verify
        assertNotNull(result);
        assertEquals(KIND, result.getKind());
        assertEquals(REPLAY_ID, result.getReplayId());
        assertEquals(cursor, result.getLastCursor());
        assertEquals(lastUpdated, result.getLastUpdatedAt());
        assertEquals(PARTITION_ID, result.getDataPartitionId());
    }

    private ReplayMetadataItem createReplayMetadataItem(String kind, String replayId) {
        ReplayMetadataItem item = new ReplayMetadataItem();
        item.setId(kind);
        item.setReplayId(replayId);
        item.setKind(kind);
        item.setOperation("replay");
        item.setTotalRecords(100L);
        item.setProcessedRecords(50L);
        item.setState("IN_PROGRESS");
        item.setStartedAt(new Date());
        item.setElapsedTime("00:10:00");
        item.setDataPartitionId(PARTITION_ID);
        return item;
    }

    private ReplayMetaDataDTO createReplayMetaDataDTO(String kind, String replayId) {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        dto.setId(kind);
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation("replay");
        dto.setTotalRecords(100L);
        dto.setProcessedRecords(50L);
        dto.setState("IN_PROGRESS");
        dto.setStartedAt(new Date());
        dto.setElapsedTime("00:10:00");
        return dto;
    }

    @Test
    public void testBatchSaveAwsReplayMetaData() {
        // Prepare test data
        List<AwsReplayMetaDataDTO> dtoList = new ArrayList<>();

        AwsReplayMetaDataDTO dto1 = new AwsReplayMetaDataDTO();
        dto1.setId("kind1");
        dto1.setReplayId(REPLAY_ID);
        dto1.setKind("kind1");
        dto1.setState("QUEUED");
        dtoList.add(dto1);

        AwsReplayMetaDataDTO dto2 = new AwsReplayMetaDataDTO();
        dto2.setId("kind2");
        dto2.setReplayId(REPLAY_ID);
        dto2.setKind("kind2");
        dto2.setState("QUEUED");
        dtoList.add(dto2);

        // Mock behavior
        when(batchWriteResult.unprocessedPutItemsForTable(any())).thenReturn(Collections.emptyList());
        when(dynamoDBQueryHelper.batchSave(anyList())).thenReturn(batchWriteResult);

        // Execute
        List<ReplayMetadataItem> result =
                replayRepository.batchSaveAwsReplayMetaData(dtoList);

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify the batch save was called with the correct number of items
        ArgumentCaptor<List<ReplayMetadataItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(dynamoDBQueryHelper).batchSave(itemsCaptor.capture());

        List<ReplayMetadataItem> capturedItems = itemsCaptor.getValue();
        assertEquals(2, capturedItems.size());
        assertEquals("kind1", capturedItems.get(0).getKind());
        assertEquals("kind2", capturedItems.get(1).getKind());
    }

    @Test
    public void testBatchSaveAwsReplayMetaData_HandlesEmptyList() {
        // Execute
        List<ReplayMetadataItem> result =
                replayRepository.batchSaveAwsReplayMetaData(Collections.emptyList());

        // Verify
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Verify batch save was not called
        verify(dynamoDBQueryHelper, never()).batchSave(anyList());
    }

    @Test
    public void testBatchSaveAwsReplayMetaData_HandlesFailures() {
        // Prepare test data
        List<AwsReplayMetaDataDTO> dtoList = new ArrayList<>();

        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setId("kind1");
        dto.setReplayId(REPLAY_ID);
        dto.setKind("kind1");
        dto.setState("QUEUED");
        dtoList.add(dto);

        // Create a failed batch
        ReplayMetadataItem failedItem = new ReplayMetadataItem();
        when(batchWriteResult.unprocessedPutItemsForTable(any())).thenReturn(List.of(failedItem));

        // Mock behavior
        when(dynamoDBQueryHelper.batchSave(anyList())).thenReturn(batchWriteResult);

        // Execute
        List<ReplayMetadataItem> unprocessedItem =
                replayRepository.batchSaveAwsReplayMetaData(dtoList);

        // Verify
        assertNotNull(unprocessedItem);
        assertEquals(1, unprocessedItem.size());

        // Verify error was logged
        verify(logger).error("Failed to save 1 batches during batch save operation");
    }

    @Test
    public void testBatchSaveAwsReplayMetaData_HandlesExceptions() {
        // Prepare test data
        List<AwsReplayMetaDataDTO> dtoList = new ArrayList<>();

        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setId("kind1");
        dto.setReplayId(REPLAY_ID);
        dto.setKind("kind1");
        dto.setState("QUEUED");
        dtoList.add(dto);

        // Mock behavior to throw exception
        when(dynamoDBQueryHelper.batchSave(anyList())).thenThrow(new RuntimeException("Test exception"));

        // Execute - should throw the exception
        try {
            replayRepository.batchSaveAwsReplayMetaData(dtoList);
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage());
        }

        // Verify error was logged
        verify(logger).error(contains("Error during batch save"), any(RuntimeException.class));
    }
    
    @Test
    public void testBatchGetAwsReplayStatusByKindsAndReplayId() {
        // Prepare test data
        List<String> kinds = Arrays.asList("kind1", "kind2", "kind3");
        Set<String> kindSet = new HashSet<>(kinds);
        
        // Create test items
        ReplayMetadataItem item1 = createReplayMetadataItem("kind1", REPLAY_ID);
        ReplayMetadataItem item2 = createReplayMetadataItem("kind2", REPLAY_ID);
        ReplayMetadataItem item3 = createReplayMetadataItem("kind3", REPLAY_ID);
        List<ReplayMetadataItem> items = Arrays.asList(item1, item2, item3);
        
        // Mock behavior
        when(dynamoDBQueryHelper.batchLoadByCompositePrimaryKey(
                kindSet,
                REPLAY_ID)).thenReturn(items);
        
        // Execute
        List<AwsReplayMetaDataDTO> results = replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(kinds, REPLAY_ID);
        
        // Verify
        assertNotNull(results);
        assertEquals(3, results.size());
        assertEquals("kind1", results.get(0).getKind());
        assertEquals("kind2", results.get(1).getKind());
        assertEquals("kind3", results.get(2).getKind());
        
        // Verify the batch load was called with the correct parameters
        verify(dynamoDBQueryHelper).batchLoadByCompositePrimaryKey(
                kindSet,
                REPLAY_ID);
    }
    
    @Test
    public void testBatchGetAwsReplayStatusByKindsAndReplayId_HandlesEmptyList() {
        // Execute with empty list
        List<AwsReplayMetaDataDTO> results = replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(Collections.emptyList(), REPLAY_ID);
        
        // Verify
        assertNotNull(results);
        assertTrue(results.isEmpty());
        
        // Verify batch load was not called
        verify(dynamoDBQueryHelper, never()).batchLoadByCompositePrimaryKey( any(), any());
    }
    
    @Test
    public void testBatchGetAwsReplayStatusByKindsAndReplayId_HandlesException() {
        // Prepare test data
        List<String> kinds = Arrays.asList("kind1", "kind2");
        Set<String> kindSet = new HashSet<>(kinds);
        
        // Mock behavior to throw exception
        when(dynamoDBQueryHelper.batchLoadByCompositePrimaryKey(
                kindSet,
                REPLAY_ID)).thenThrow(new RuntimeException("Test exception"));
        
        // Mock individual retrievals for fallback
        when(dynamoDBQueryHelper.getItem("kind1", REPLAY_ID))
            .thenReturn(Optional.of(createReplayMetadataItem("kind1", REPLAY_ID)));
        when(dynamoDBQueryHelper.getItem("kind2", REPLAY_ID))
            .thenReturn(Optional.of(createReplayMetadataItem("kind2", REPLAY_ID)));
        
        // Execute
        List<AwsReplayMetaDataDTO> results = replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(kinds, REPLAY_ID);
        
        // Verify
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("kind1", results.get(0).getKind());
        assertEquals("kind2", results.get(1).getKind());
        
        // Verify error was logged
        verify(logger).error(contains("Error during batch retrieval"), any(RuntimeException.class));
        verify(logger).info("Falling back to individual retrievals");
        
        // Verify individual retrievals were made
        verify(dynamoDBQueryHelper).getItem("kind1", REPLAY_ID);
        verify(dynamoDBQueryHelper).getItem("kind2", REPLAY_ID);
    }

}
