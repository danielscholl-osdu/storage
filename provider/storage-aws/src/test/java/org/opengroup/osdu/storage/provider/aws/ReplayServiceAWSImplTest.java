package org.opengroup.osdu.storage.provider.aws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;

import java.util.*;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayServiceAWSImplTest {

    @Mock
    private IReplayRepository replayRepository;

    @Mock
    private ReplayMessageHandler messageHandler;

    @Mock
    private QueryRepositoryImpl queryRepository;

    @Mock
    private IMessageBus messageBus;

    @Mock
    private DpsHeaders headers;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ParallelReplayProcessor parallelReplayProcessor;

    @Mock
    private ReplayBatchConfig batchConfig;

    @Mock
    private ExecutorService executorService;

    @Mock
    private RequestScopeUtil requestScopeUtil;

    private ReplayServiceAWSImpl replayService;

    @Before
    public void setup() {
        when(headers.getHeaders()).thenReturn(Collections.singletonMap("data-partition-id", "test-partition"));
        
        replayService = new ReplayServiceAWSImpl(
                replayRepository,
                messageHandler,
                queryRepository,
                messageBus,
                headers,
                auditLogger,
                logger,
                objectMapper,
                parallelReplayProcessor,
                batchConfig,
                executorService,
                requestScopeUtil
        );
    }

    @Test
    public void testHandleReplayRequest_WithSpecifiedKinds_ShouldCreateMetadataAndStartProcessing() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setReplayId("test-replay-id");
        request.setOperation("replay");
        
        ReplayFilter filter = new ReplayFilter();
        List<String> kinds = Arrays.asList("kind1", "kind2");
        filter.setKinds(kinds);
        request.setFilter(filter);

        // Mock the queryRepository to return active records for the test kinds
        Map<String, Long> kindCounts = new HashMap<>();
        kindCounts.put("kind1", 100L);
        kindCounts.put("kind2", 200L);
        when(queryRepository.getActiveRecordsCountForKinds(kinds)).thenReturn(kindCounts);

        // Act
        ReplayResponse response = replayService.handleReplayRequest(request);

        // Assert
        assertEquals("test-replay-id", response.getReplayId());
        
        // Verify metadata records were created - 3 calls:
        // 1 for initial status record + 2 for the kinds
        verify(replayRepository, times(3)).save(any(ReplayMetaDataDTO.class));
        
        // Verify parallel processing was started
        verify(parallelReplayProcessor).processReplayAsync(eq(request), eq(Arrays.asList("kind1", "kind2")));
        
        // Verify audit logging
        verify(auditLogger).createReplayRequestSuccess(anyList());
    }

    @Test
    public void testHandleReplayRequest_WithoutSpecifiedKinds_ShouldStartAsyncProcessing() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setReplayId("test-replay-id");
        request.setOperation("replay");
        
        // No kinds specified in the filter
        
        // Act
        ReplayResponse response = replayService.handleReplayRequest(request);

        // Assert
        assertEquals("test-replay-id", response.getReplayId());
        
        // Verify executor service was used to start async processing
        verify(executorService).submit(any(Runnable.class));
        
        // Verify audit logging
        verify(auditLogger).createReplayRequestSuccess(anyList());
    }

    @Test
    public void testGetReplayStatus_ShouldReturnCorrectStatus() {
        // Arrange
        String replayId = "test-replay-id";
        
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setReplayId(replayId);
        metadata1.setKind("kind1");
        metadata1.setOperation("replay");
        metadata1.setState(ReplayState.COMPLETED.name());
        metadata1.setTotalRecords(100L);
        metadata1.setProcessedRecords(100L);
        metadata1.setStartedAt(new Date());
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setReplayId(replayId);
        metadata2.setKind("kind2");
        metadata2.setOperation("replay");
        metadata2.setState(ReplayState.IN_PROGRESS.name());
        metadata2.setTotalRecords(200L);
        metadata2.setProcessedRecords(50L);
        metadata2.setStartedAt(new Date());
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Act
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        assertEquals("replay", response.getOperation());
        assertEquals(ReplayState.IN_PROGRESS.name(), response.getOverallState());
        // Use explicit long value to avoid ambiguous method call
        assertEquals(300L, response.getTotalRecords().longValue());
        assertEquals(150L, response.getProcessedRecords().longValue());
        assertEquals(2, response.getStatus().size());
    }

    @Test
    public void testCreateInitialMetadataRecords_ShouldCreateRecordsWithZeroCounts() {
        // Arrange
        String replayId = "test-replay-id";
        List<String> kinds = Arrays.asList("kind1", "kind2");
        String operation = "replay";
        
        ArgumentCaptor<ReplayMetaDataDTO> metadataCaptor = ArgumentCaptor.forClass(ReplayMetaDataDTO.class);
        
        // Act
        // Call the private method using reflection
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "createInitialMetadataRecords", String.class, List.class, String.class);
            method.setAccessible(true);
            method.invoke(replayService, replayId, kinds, operation);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        verify(replayRepository, times(2)).save(metadataCaptor.capture());
        
        List<ReplayMetaDataDTO> capturedMetadata = metadataCaptor.getAllValues();
        assertEquals(2, capturedMetadata.size());
        
        for (ReplayMetaDataDTO metadata : capturedMetadata) {
            assertEquals(replayId, metadata.getReplayId());
            assertTrue(kinds.contains(metadata.getKind()));
            assertEquals(operation, metadata.getOperation());
            assertEquals(ReplayState.QUEUED.name(), metadata.getState());
            assertEquals(0L, metadata.getTotalRecords().longValue());
            assertEquals(0L, metadata.getProcessedRecords().longValue());
            assertNotNull(metadata.getStartedAt());
        }
    }
}
