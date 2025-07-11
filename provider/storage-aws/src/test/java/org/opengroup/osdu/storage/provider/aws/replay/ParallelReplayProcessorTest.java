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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.request.ReplayRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ParallelReplayProcessorTest {

    @Mock
    private ExecutorService executorService;

    @Mock
    private ReplayBatchConfig batchConfig;

    @Mock
    private ReplayRepositoryImpl replayRepository;

    @Mock
    private ReplayMessageHandler messageHandler;

    @Mock
    private DpsHeaders headers;

    @Mock
    private QueryRepositoryImpl queryRepository;

    @Mock
    private RequestScopeUtil requestScopeUtil;

    @Mock
    private BatchWriteResult batchWriteResult;

    private ParallelReplayProcessor processor;
    private ReplayRequest testRequest;
    private List<String> testKinds;
    private Map<String, String> testHeaders;

    @Before
    public void setup() {
        when(batchConfig.getBatchSize()).thenReturn(50);
        
        testHeaders = new HashMap<>();
        testHeaders.put("data-partition-id", "test-partition");
        testHeaders.put("authorization", "Bearer test-token");
        
        when(headers.getHeaders()).thenReturn(testHeaders);
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(headers.getPartitionId()).thenReturn("test-partition");
        
        // Setup requestScopeUtil to execute the runnable immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
        
        processor = new ParallelReplayProcessor(
                executorService,
                batchConfig,
                replayRepository,
                messageHandler,
                headers,
                queryRepository,
                requestScopeUtil
        );
        
        // Setup common test data
        testRequest = new ReplayRequest();
        testRequest.setReplayId("test-replay-id");
        testRequest.setOperation("replay");
        
        testKinds = Arrays.asList("kind1", "kind2", "kind3");
    }

    @Test
    public void testProcessReplayAsync_ShouldSubmitTaskToExecutorService() {
        // Act
        processor.processReplayAsync(testRequest, testKinds);
        
        // Assert
        verify(executorService).submit(any(Runnable.class));
    }
    
    @Test
    public void testProcessReplayAsync_WithNullExecutorService_ShouldNotFail() {
        // Arrange
        processor = new ParallelReplayProcessor(
                null, // null executor service
                batchConfig,
                replayRepository,
                messageHandler,
                headers,
                queryRepository,
                requestScopeUtil
        );
        
        // Act - should not throw exception
        processor.processReplayAsync(testRequest, testKinds);
        
        // Assert - verify no exceptions were thrown
        assertTrue("Test passed without exceptions", true);
    }

    @Test
    public void testProcessReplayInBackground_ShouldUpdateCountsAndProcessBatches() throws ReplayMessageHandlerException {
        // Arrange
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        
        // Create test metadata DTOs for each kind
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : testKinds) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(testRequest.getReplayId());
            dto.setState("PENDING");
            batchResults.add(dto);
        }
        
        // Setup mocks for record counts
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        kindCounts.put("kind3", 300L);
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);
        
        // Mock the batch get calls to return our prepared data
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(batchResults);
        
        // Mock the batch save to return empty list (no failures)
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        processor.processReplayAsync(testRequest, testKinds);
        
        // Capture and execute the runnable
        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        
        // Assert
        // Verify the request scope util was called with the correct headers
        verify(requestScopeUtil).executeInRequestScope(any(Runnable.class), eq(testHeaders));
        
        // Verify record counts were updated
        verify(queryRepository).getActiveRecordsCountForKinds(anyList());
        
        // Verify batch status updates were called
        verify(replayRepository, times(2)).batchSaveAwsReplayMetaData(anyList());
        
        // Verify messages were sent
        verify(messageHandler).sendReplayMessage(anyList(), eq("replay"));
    }
    
    @Test
    public void testProcessReplayInBackground_HandlesExceptions() {
        // Arrange
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        
        // Setup exception to be thrown during processing
        doThrow(new RuntimeException("Test exception")).when(queryRepository).getActiveRecordsCountForKinds(anyList());
        
        // Act
        processor.processReplayAsync(testRequest, testKinds);
        
        // Capture and execute the runnable - should not throw exception outside
        verify(executorService).submit(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        
        // Assert - verify no exceptions were thrown
        assertTrue("Test passed without exceptions", true);
    }

    @Test
    public void testUpdateRecordCountsForBatch_UsesBatchSave() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        setupReplayMetadataForKinds(replayId, testKinds);
        
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        kindCounts.put("kind3", 300L);
        
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);
        
        // Create test metadata DTOs for each kind
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : testKinds) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(replayId);
            dto.setTotalRecords(0L);
            batchResults.add(dto);
        }
        
        // Mock the batch get calls to return our prepared data
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(batchResults);
        
        // Mock the batch save to return empty list (no failures)
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateRecordCountsForBatch", List.class, String.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId);
        
        // Assert
        verify(replayRepository).batchSaveAwsReplayMetaData(anyList());
        verify(replayRepository, never()).saveAwsReplayMetaData(any(AwsReplayMetaDataDTO.class));
    }
    
    @Test
    public void testUpdateRecordCountsForBatch_HandlesExceptions() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        // Setup exception to be thrown during query
        doThrow(new RuntimeException("Test exception")).when(queryRepository).getActiveRecordsCountForKinds(anyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateRecordCountsForBatch", List.class, String.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId);
        
        // Assert - verify no exceptions were thrown and batch save was not called
        verify(replayRepository, never()).batchSaveAwsReplayMetaData(anyList());
    }

    @Test
    public void testUpdateBatchStatus_UsesBatchSave() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        // Create test metadata DTOs for each kind
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : testKinds) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(replayId);
            dto.setState("PENDING");
            batchResults.add(dto);
        }
        
        // Mock the batch get calls to return our prepared data
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(batchResults);
        
        // Mock the batch save to return empty list (no failures)
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateBatchStatus", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId, ReplayState.QUEUED);
        
        // Assert
        verify(replayRepository).batchSaveAwsReplayMetaData(anyList());
    }
    
    @Test
    public void testUpdateBatchStatus_HandlesFailedBatches() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        // Create test metadata DTOs for each kind
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : testKinds) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(replayId);
            dto.setState("PENDING");
            batchResults.add(dto);
        }
        
        // Create a failed batch
        ReplayMetadataItem failedItem = new ReplayMetadataItem();
        failedItem.setId("id");
        
        // Mock the batch get calls to return our prepared data
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(batchResults);
        
        // Mock the batch save to return a failed batch
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(List.of(failedItem));
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateBatchStatus", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId, ReplayState.QUEUED);
        
        // Assert
        verify(replayRepository).batchSaveAwsReplayMetaData(anyList());
    }

    @Test
    public void testUpdateRecordCounts_UsesBatchSave() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        setupReplayMetadataForKinds(replayId, testKinds);
        
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        kindCounts.put("kind3", 300L);
        
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod("updateRecordCounts", String.class, List.class);
        method.setAccessible(true);
        method.invoke(processor, replayId, testKinds);
        
        // Assert
        verify(replayRepository).batchSaveAwsReplayMetaData(anyList());
        verify(replayRepository, never()).saveAwsReplayMetaData(any(AwsReplayMetaDataDTO.class));
    }

    @Test
    public void testProcessBatches_ProcessesAllBatches() throws Exception {
        // Arrange
        List<List<String>> batches = new ArrayList<>();
        batches.add(Arrays.asList("kind1", "kind2"));
        batches.add(Collections.singletonList("kind3"));
        
        // Create test metadata DTOs for each kind
        List<AwsReplayMetaDataDTO> batchResults1 = new ArrayList<>();
        for (String kind : Arrays.asList("kind1", "kind2")) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(testRequest.getReplayId());
            dto.setState("PENDING");
            batchResults1.add(dto);
        }
        
        List<AwsReplayMetaDataDTO> batchResults2 = new ArrayList<>();
        AwsReplayMetaDataDTO dto3 = new AwsReplayMetaDataDTO();
        dto3.setKind("kind3");
        dto3.setReplayId(testRequest.getReplayId());
        dto3.setState("PENDING");
        batchResults2.add(dto3);
        
        // Mock the batch get calls to return our prepared data
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(eq(Arrays.asList("kind1", "kind2")), anyString()))
            .thenReturn(batchResults1);
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(eq(Collections.singletonList("kind3")), anyString()))
            .thenReturn(batchResults2);
        
        // Mock the batch save to return empty list (no failures)
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "processBatches", ReplayRequest.class, List.class);
        method.setAccessible(true);
        
        try {
            method.invoke(processor, testRequest, batches);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof ReplayMessageHandlerException) {
                throw (ReplayMessageHandlerException) e.getCause();
            }
            throw e;
        }
        
        // Assert
        // Verify batch status updates were called
        verify(replayRepository, times(2)).batchSaveAwsReplayMetaData(anyList());
        
        // Verify message handler was called twice (once per batch)
        verify(messageHandler, times(2)).sendReplayMessage(anyList(), eq("replay"));
    }

    @Test
    public void testRetrieveMetadataWithState_UsesBatchLoad() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        List<String> batch = Arrays.asList("kind1", "kind2", "kind3");
        
        // Create test metadata DTOs
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : batch) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(replayId);
            dto.setState("PENDING");
            batchResults.add(dto);
        }
        
        // Mock the batch load method
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId))
            .thenReturn(batchResults);
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "retrieveMetadataWithState", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        List<AwsReplayMetaDataDTO> result = (List<AwsReplayMetaDataDTO>) method.invoke(
                processor, batch, replayId, ReplayState.QUEUED);
        
        // Assert
        // Verify batch load was called
        verify(replayRepository).batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
        
        // Verify individual loads were not called
        verify(replayRepository, never()).getAwsReplayStatusByKindAndReplayId(anyString(), anyString());
        
        // Verify the state was updated for all items
        assertEquals(3, result.size());
        for (AwsReplayMetaDataDTO dto : result) {
            assertEquals(ReplayState.QUEUED.name(), dto.getState());
            assertNotNull(dto.getLastUpdatedAt());
        }
    }
    
    @Test
    public void testRetrieveMetadataWithState_HandlesBatchLoadException() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        List<String> batch = Arrays.asList("kind1", "kind2");
        
        // Mock batch load to throw exception
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId))
            .thenThrow(new RuntimeException("Test batch load exception"));
        
        // Mock individual loads for fallback
        AwsReplayMetaDataDTO dto1 = new AwsReplayMetaDataDTO();
        dto1.setKind("kind1");
        dto1.setReplayId(replayId);
        dto1.setState("PENDING");
        
        AwsReplayMetaDataDTO dto2 = new AwsReplayMetaDataDTO();
        dto2.setKind("kind2");
        dto2.setReplayId(replayId);
        dto2.setState("PENDING");
        
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind1", replayId)).thenReturn(dto1);
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind2", replayId)).thenReturn(dto2);
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "retrieveMetadataWithState", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        List<AwsReplayMetaDataDTO> result = (List<AwsReplayMetaDataDTO>) method.invoke(
                processor, batch, replayId, ReplayState.QUEUED);
        
        // Assert
        // Verify batch load was attempted
        verify(replayRepository).batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
        
        // Verify fallback to individual loads
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId("kind1", replayId);
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId("kind2", replayId);
        
        // Verify the state was updated for all items
        assertEquals(2, result.size());
        for (AwsReplayMetaDataDTO dto : result) {
            assertEquals(ReplayState.QUEUED.name(), dto.getState());
            assertNotNull(dto.getLastUpdatedAt());
        }
    }
    
    @Test
    public void testRetrieveMetadataRecordsWithCounts_UsesBatchLoad() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        List<String> batch = Arrays.asList("kind1", "kind2", "kind3");
        
        // Create test metadata DTOs
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        for (String kind : batch) {
            AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
            dto.setKind(kind);
            dto.setReplayId(replayId);
            dto.setTotalRecords(0L);
            batchResults.add(dto);
        }
        
        // Create kind counts
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        kindCounts.put("kind3", 300L);
        
        // Mock the batch load method
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId))
            .thenReturn(batchResults);
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "retrieveMetadataRecordsWithCounts", List.class, String.class, Map.class);
        method.setAccessible(true);
        List<AwsReplayMetaDataDTO> result = (List<AwsReplayMetaDataDTO>) method.invoke(
                processor, batch, replayId, kindCounts);
        
        // Assert
        // Verify batch load was called
        verify(replayRepository).batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
        
        // Verify individual loads were not called
        verify(replayRepository, never()).getAwsReplayStatusByKindAndReplayId(anyString(), anyString());
        
        // Verify the counts were updated for all items
        assertEquals(3, result.size());
        for (AwsReplayMetaDataDTO dto : result) {
            String kind = dto.getKind();
            assertEquals(kindCounts.get(kind).longValue(), dto.getTotalRecords().longValue());
            assertNotNull(dto.getLastUpdatedAt());
        }
    }
    
    @Test
    public void testRetrieveMetadataRecordsWithCounts_HandlesBatchLoadException() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        List<String> batch = Arrays.asList("kind1", "kind2");
        
        // Create kind counts
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        
        // Mock batch load to throw exception
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId))
            .thenThrow(new RuntimeException("Test batch load exception"));
        
        // Mock individual loads for fallback
        AwsReplayMetaDataDTO dto1 = new AwsReplayMetaDataDTO();
        dto1.setKind("kind1");
        dto1.setReplayId(replayId);
        dto1.setTotalRecords(0L);
        
        AwsReplayMetaDataDTO dto2 = new AwsReplayMetaDataDTO();
        dto2.setKind("kind2");
        dto2.setReplayId(replayId);
        dto2.setTotalRecords(0L);
        
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind1", replayId)).thenReturn(dto1);
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind2", replayId)).thenReturn(dto2);
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "retrieveMetadataRecordsWithCounts", List.class, String.class, Map.class);
        method.setAccessible(true);
        List<AwsReplayMetaDataDTO> result = (List<AwsReplayMetaDataDTO>) method.invoke(
                processor, batch, replayId, kindCounts);
        
        // Assert
        // Verify batch load was attempted
        verify(replayRepository).batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
        
        // Verify fallback to individual loads
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId("kind1", replayId);
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId("kind2", replayId);
        
        // Verify the counts were updated for all items
        assertEquals(2, result.size());
        for (AwsReplayMetaDataDTO dto : result) {
            String kind = dto.getKind();
            assertEquals(kindCounts.get(kind).longValue(), dto.getTotalRecords().longValue());
            assertNotNull(dto.getLastUpdatedAt());
        }
    }

    @Test
    public void testCreateReplayMessages_ShouldCreateCorrectMessages() throws Exception {
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "createReplayMessages", ReplayRequest.class, List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ReplayMessage> messages = (List<ReplayMessage>) method.invoke(processor, testRequest, testKinds);
        
        // Assert
        assertNotNull(messages);
        assertEquals(3, messages.size());
        
        for (int i = 0; i < messages.size(); i++) {
            ReplayMessage message = messages.get(i);
            ReplayData body = message.getBody();
            
            assertEquals("test-replay-id", body.getReplayId());
            assertEquals(testKinds.get(i), body.getKind());
            assertEquals("replay", body.getOperation());
            assertEquals(ReplayType.REPLAY_KIND.name(), body.getReplayType());
            assertNotNull(body.getId());
            assertNotNull(body.getStartAtTimestamp());
            
            // Check headers
            Map<String, String> messageHeaders = message.getHeaders();
            assertNotNull(messageHeaders);
            assertEquals("test-partition", messageHeaders.get("data-partition-id"));
            assertTrue(messageHeaders.containsKey("correlation-id"));
            // Correlation ID should include the original ID plus the counter
            assertTrue(messageHeaders.get("correlation-id").startsWith("test-correlation-id"));
        }
    }
    
    @Test
    public void testCreateReplayMessage_CreatesCorrectMessage() throws Exception {
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "createReplayMessage", String.class, String.class, String.class, int.class);
        method.setAccessible(true);
        ReplayMessage message = (ReplayMessage) method.invoke(
                processor, "test-kind", "test-replay-id", "test-operation", 5);
        
        // Assert
        assertNotNull(message);
        
        ReplayData body = message.getBody();
        assertEquals("test-replay-id", body.getReplayId());
        assertEquals("test-kind", body.getKind());
        assertEquals("test-operation", body.getOperation());
        assertEquals(ReplayType.REPLAY_KIND.name(), body.getReplayType());
        assertNotNull(body.getId());
        assertNotNull(body.getStartAtTimestamp());
        
        Map<String, String> messageHeaders = message.getHeaders();
        assertNotNull(messageHeaders);
        assertEquals("test-partition", messageHeaders.get("data-partition-id"));
        assertTrue(messageHeaders.get("correlation-id").contains("test-correlation-id"));
    }

    @Test
    public void testCreateBatches_ShouldCreateCorrectBatches() throws Exception {
        // Arrange
        List<String> kinds = new ArrayList<>();
        for (int i = 0; i < 125; i++) {
            kinds.add("kind" + i);
        }
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "createBatches", List.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<String>> batches = (List<List<String>>) method.invoke(processor, kinds, 50);
        
        // Assert
        assertNotNull(batches);
        assertEquals(3, batches.size());
        assertEquals(50, batches.get(0).size());
        assertEquals(50, batches.get(1).size());
        assertEquals(25, batches.get(2).size());
        
        // Check that all kinds are included
        Set<String> allKinds = new HashSet<>();
        for (List<String> batch : batches) {
            allKinds.addAll(batch);
        }
        assertEquals(125, allKinds.size());
    }
    
    @Test
    public void testCreateBatches_WithEmptyList_ShouldReturnEmptyList() throws Exception {
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "createBatches", List.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<String>> batches = (List<List<String>>) method.invoke(processor, Collections.emptyList(), 50);
        
        // Assert
        assertNotNull(batches);
        assertTrue(batches.isEmpty());
    }
    
    @Test
    public void testCreateBatches_WithSingleItem_ShouldReturnSingleBatch() throws Exception {
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "createBatches", List.class, int.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<String>> batches = (List<List<String>>) method.invoke(processor, Collections.singletonList("kind1"), 50);
        
        // Assert
        assertNotNull(batches);
        assertEquals(1, batches.size());
        assertEquals(1, batches.get(0).size());
        assertEquals("kind1", batches.get(0).get(0));
    }
    
    @Test
    public void testCleanup_ShouldShutdownExecutorService() {
        // Act
        processor.cleanup();
        
        // Assert
        verify(executorService).shutdown();
    }
    
    @Test
    public void testCleanup_WithNullExecutorService_ShouldNotFail() {
        // Arrange
        processor = new ParallelReplayProcessor(
                null, // null executor service
                batchConfig,
                replayRepository,
                messageHandler,
                headers,
                queryRepository,
                requestScopeUtil
        );
        
        // Act - should not throw exception
        processor.cleanup();
        
        // Assert - verify no exceptions were thrown
        assertTrue("Test passed without exceptions", true);
    }

    @Test
    public void testProcessReplayAsync_WithEmptyKindsList() throws ReplayMessageHandlerException {
        // Arrange
        List<String> emptyKinds = Collections.emptyList();
        
        // Act
        processor.processReplayAsync(testRequest, emptyKinds);
        
        // Capture the runnable
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).submit(runnableCaptor.capture());
        
        // Execute the runnable
        runnableCaptor.getValue().run();
        
        // Assert - no exceptions should be thrown
        // No messages should be sent
        verify(messageHandler, never()).sendReplayMessage(anyList(), anyString());
    }

    @Test
    public void testProcessReplayAsync_WithLargeNumberOfKinds() throws ReplayMessageHandlerException {
        // Arrange
        List<String> manyKinds = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            manyKinds.add("kind" + i);
        }
        
        // Mock behavior for record counts
        Map<String, Long> kindCounts = new HashMap<>();
        for (String kind : manyKinds) {
            kindCounts.put(kind, 100L);
            
            // Setup metadata for each kind
            AwsReplayMetaDataDTO metadata = new AwsReplayMetaDataDTO();
            metadata.setReplayId(testRequest.getReplayId());
            metadata.setKind(kind);
        }
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);
        
        // Act
        processor.processReplayAsync(testRequest, manyKinds);
        
        // Capture the runnable
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).submit(runnableCaptor.capture());
        
        // Execute the runnable
        runnableCaptor.getValue().run();
        
        // Assert - verify messages were sent in batches
        // With batch size 50, we should have 20 batches
        verify(messageHandler, times(20)).sendReplayMessage(anyList(), eq("replay"));
    }

    @Test
    public void testProcessReplayAsync_WithCustomOperation() throws ReplayMessageHandlerException {
        // Arrange
        testRequest.setOperation("reindex");
        
        setupMocksForFullFlow();
        
        // Act
        processor.processReplayAsync(testRequest, testKinds);
        
        // Capture the runnable
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).submit(runnableCaptor.capture());
        
        // Execute the runnable
        runnableCaptor.getValue().run();
        
        // Assert - verify messages were sent with correct operation
        verify(messageHandler).sendReplayMessage(anyList(), eq("reindex"));
    }

    // Helper methods
    
    private void setupMocksForFullFlow() {
        // Setup replay metadata
        setupReplayMetadataForKinds(testRequest.getReplayId(), testKinds);
        
        // Setup record counts
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        kindCounts.put("kind3", 300L);
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);

        // Setup empty results for batch get to force the code to use the individual retrievals
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(Collections.emptyList());
    }
    
    private void setupReplayMetadataForKinds(String replayId, List<String> kinds) {
        List<AwsReplayMetaDataDTO> batchResults = new ArrayList<>();
        
        for (String kind : kinds) {
            AwsReplayMetaDataDTO metadata = new AwsReplayMetaDataDTO();
            metadata.setReplayId(replayId);
            metadata.setKind(kind);
            metadata.setTotalRecords(0L);
            // Add to batch results
            batchResults.add(metadata);
        }
        
        // Mock the batch get to return the batch results
        when(replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(anyList(), anyString()))
            .thenReturn(batchResults);
    }
}
