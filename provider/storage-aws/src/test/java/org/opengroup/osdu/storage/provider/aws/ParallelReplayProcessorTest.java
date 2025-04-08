package org.opengroup.osdu.storage.provider.aws;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayRequest;

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
    private IReplayRepository replayRepository;

    @Mock
    private ReplayMessageHandler messageHandler;

    @Mock
    private DpsHeaders headers;

    @Mock
    private QueryRepositoryImpl queryRepository;

    @Mock
    private RequestScopeUtil requestScopeUtil;

    private ParallelReplayProcessor processor;

    @Before
    public void setup() {
        when(batchConfig.getBatchSize()).thenReturn(50);
        Map<String, String> headerMap = Collections.singletonMap("data-partition-id", "test-partition");
        when(headers.getHeaders()).thenReturn(headerMap);
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
    }

    @Test
    public void testProcessReplayAsync_ShouldSubmitTaskToExecutorService() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setReplayId("test-replay-id");
        request.setOperation("replay");
        
        List<String> kinds = Arrays.asList("kind1", "kind2");
        
        // Capture the runnable submitted to the executor service
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        
        // Act
        processor.processReplayAsync(request, kinds);
        
        // Assert
        verify(executorService).submit(runnableCaptor.capture());
        
        // Execute the captured runnable to trigger the requestScopeUtil call
        runnableCaptor.getValue().run();
        
        // Now verify that requestScopeUtil was called with any Runnable and any Map
        verify(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
    }

    @Test
    public void testUpdateRecordCounts_ShouldUpdateMetadataWithCounts() {
        // Arrange
        String replayId = "test-replay-id";
        List<String> kinds = Arrays.asList("kind1", "kind2");
        
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setReplayId(replayId);
        metadata1.setKind("kind1");
        metadata1.setTotalRecords(0L);
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setReplayId(replayId);
        metadata2.setKind("kind2");
        metadata2.setTotalRecords(0L);
        
        when(replayRepository.getReplayStatusByKindAndReplayId("kind1", replayId)).thenReturn(metadata1);
        when(replayRepository.getReplayStatusByKindAndReplayId("kind2", replayId)).thenReturn(metadata2);
        
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        
        when(queryRepository.getActiveRecordsCountForKinds(anyList())).thenReturn(kindCounts);
        
        // Act
        // Call the private method using reflection
        try {
            java.lang.reflect.Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                    "updateRecordCounts", String.class, List.class);
            method.setAccessible(true);
            method.invoke(processor, replayId, kinds);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        ArgumentCaptor<ReplayMetaDataDTO> metadataCaptor = ArgumentCaptor.forClass(ReplayMetaDataDTO.class);
        verify(replayRepository, times(2)).save(metadataCaptor.capture());
        
        List<ReplayMetaDataDTO> capturedMetadata = metadataCaptor.getAllValues();
        assertEquals(2, capturedMetadata.size());
        
        for (ReplayMetaDataDTO metadata : capturedMetadata) {
            if ("kind1".equals(metadata.getKind())) {
                assertEquals(100L, metadata.getTotalRecords().longValue());
            } else if ("kind2".equals(metadata.getKind())) {
                assertEquals(200L, metadata.getTotalRecords().longValue());
            } else {
                fail("Unexpected kind: " + metadata.getKind());
            }
        }
    }

    @Test
    public void testCreateReplayMessages_ShouldCreateCorrectMessages() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setReplayId("test-replay-id");
        request.setOperation("replay");
        
        List<String> kinds = Arrays.asList("kind1", "kind2");
        
        // Act
        // Call the private method using reflection
        List<ReplayMessage> messages = null;
        try {
            java.lang.reflect.Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                    "createReplayMessages", ReplayRequest.class, List.class);
            method.setAccessible(true);
            messages = (List<ReplayMessage>) method.invoke(processor, request, kinds);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertNotNull(messages);
        assertEquals(2, messages.size());
        
        for (ReplayMessage message : messages) {
            assertEquals("test-replay-id", message.getBody().getReplayId());
            assertTrue(kinds.contains(message.getBody().getKind()));
            assertEquals("replay", message.getBody().getOperation());
            assertNotNull(message.getHeaders());
            assertTrue(message.getHeaders().containsKey("data-partition-id"));
            assertTrue(message.getHeaders().containsKey("correlation-id"));
        }
    }

    @Test
    public void testCreateBatches_ShouldCreateCorrectBatches() {
        // Arrange
        List<String> kinds = new ArrayList<>();
        for (int i = 0; i < 125; i++) {
            kinds.add("kind" + i);
        }
        
        // Act
        // Call the private method using reflection
        List<List<String>> batches = null;
        try {
            java.lang.reflect.Method method = ParallelReplayProcessor.class.getDeclaredMethod(
                    "createBatches", List.class, int.class);
            method.setAccessible(true);
            batches = (List<List<String>>) method.invoke(processor, kinds, 50);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertNotNull(batches);
        assertEquals(3, batches.size());
        assertEquals(50, batches.get(0).size());
        assertEquals(50, batches.get(1).size());
        assertEquals(25, batches.get(2).size());
    }
}
