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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.request.ReplayRequest;

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
        
        // Setup mocks for the full flow
        setupMocksForFullFlow();
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
        verify(replayRepository, atLeastOnce()).batchSaveAwsReplayMetaData(anyList());
        
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
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateRecordCountsForBatch", List.class, String.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId);
        
        // Assert
        ArgumentCaptor<List<AwsReplayMetaDataDTO>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(replayRepository).batchSaveAwsReplayMetaData(batchCaptor.capture());
        
        List<AwsReplayMetaDataDTO> capturedBatch = batchCaptor.getValue();
        assertEquals(3, capturedBatch.size());
        
        Map<String, Long> expectedCounts = new HashMap<>();
        expectedCounts.put("kind1", 100L);
        expectedCounts.put("kind2", 200L);
        expectedCounts.put("kind3", 300L);
        
        for (AwsReplayMetaDataDTO metadata : capturedBatch) {
            String kind = metadata.getKind();
            assertTrue("Unexpected kind: " + kind, expectedCounts.containsKey(kind));
            assertEquals(expectedCounts.get(kind).longValue(), metadata.getTotalRecords().longValue());
            assertNotNull(metadata.getLastUpdatedAt());
        }
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
        
        setupReplayMetadataForKinds(replayId, testKinds);
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateBatchStatus", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId, ReplayState.QUEUED);
        
        // Assert
        ArgumentCaptor<List<AwsReplayMetaDataDTO>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(replayRepository).batchSaveAwsReplayMetaData(batchCaptor.capture());
        
        List<AwsReplayMetaDataDTO> capturedBatch = batchCaptor.getValue();
        assertEquals(3, capturedBatch.size());
        
        for (AwsReplayMetaDataDTO metadata : capturedBatch) {
            assertEquals(ReplayState.QUEUED.name(), metadata.getState());
            assertNotNull(metadata.getLastUpdatedAt());
        }
        
        // Verify individual saves were not called
        verify(replayRepository, never()).saveAwsReplayMetaData(any(AwsReplayMetaDataDTO.class));
    }
    
    @Test
    public void testUpdateBatchStatus_HandlesFailedBatches() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        setupReplayMetadataForKinds(replayId, testKinds);
        
        // Create a failed batch
        FailedBatch failedBatch = new FailedBatch();
        failedBatch.setException(new RuntimeException("Test batch failure"));
        failedBatch.setUnprocessedItems(Map.of("test", List.of()));
        
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(List.of(failedBatch));
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateBatchStatus", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId, ReplayState.QUEUED);
        
        // Assert
        verify(replayRepository).batchSaveAwsReplayMetaData(anyList());
        // Test passes if no exception is thrown
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
        
        setupReplayMetadataForKinds(testRequest.getReplayId(), testKinds);
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
        
        // Capture and verify the messages
        ArgumentCaptor<List<ReplayMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(messageHandler, times(2)).sendReplayMessage(messagesCaptor.capture(), eq("replay"));
        
        List<List<ReplayMessage>> allMessages = messagesCaptor.getAllValues();
        assertEquals(2, allMessages.size());
        assertEquals(2, allMessages.get(0).size()); // First batch has 2 kinds
        assertEquals(1, allMessages.get(1).size()); // Second batch has 1 kind
    }

    @Test
    public void testUpdateBatchStatus_HandlesExceptions() throws Exception {
        // Arrange
        String replayId = "test-replay-id";
        
        // Setup exception for the first kind
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind1", replayId))
            .thenThrow(new RuntimeException("Test exception"));
        
        // Setup normal behavior for other kinds
        AwsReplayMetaDataDTO metadata2 = new AwsReplayMetaDataDTO();
        metadata2.setKind("kind2");
        when(replayRepository.getAwsReplayStatusByKindAndReplayId("kind2", replayId)).thenReturn(metadata2);
        
        when(replayRepository.batchSaveAwsReplayMetaData(anyList())).thenReturn(Collections.emptyList());
        
        // Act
        Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                "updateBatchStatus", List.class, String.class, ReplayState.class);
        method.setAccessible(true);
        method.invoke(processor, testKinds, replayId, ReplayState.QUEUED);
        
        // Assert - should still process the batch with the second kind
        ArgumentCaptor<List<AwsReplayMetaDataDTO>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(replayRepository).batchSaveAwsReplayMetaData(batchCaptor.capture());
        
        List<AwsReplayMetaDataDTO> capturedBatch = batchCaptor.getValue();
        assertEquals(1, capturedBatch.size());
        assertEquals("kind2", capturedBatch.get(0).getKind());
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
            when(replayRepository.getAwsReplayStatusByKindAndReplayId(kind, testRequest.getReplayId()))
                .thenReturn(metadata);
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
    }
    
    private void setupReplayMetadataForKinds(String replayId, List<String> kinds) {
        for (String kind : kinds) {
            AwsReplayMetaDataDTO metadata = new AwsReplayMetaDataDTO();
            metadata.setReplayId(replayId);
            metadata.setKind(kind);
            metadata.setTotalRecords(0L);
            when(replayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId)).thenReturn(metadata);
        }
    }
}
