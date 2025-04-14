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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.replay.ParallelReplayProcessor;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayOperation;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
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
    private QueryRepositoryImpl queryRepository;

    @Mock
    private DpsHeaders headers;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private ParallelReplayProcessor parallelReplayProcessor;

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
                queryRepository,
                headers,
                auditLogger,
                parallelReplayProcessor,
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
        verify(parallelReplayProcessor).processReplayAsync(request, Arrays.asList("kind1", "kind2"));
        
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
    
    @Test
    public void testGetReplayStatus_WithSystemRecordOnly_ShouldReturnCorrectStatus() {
        // Arrange
        String replayId = "test-replay-id";
        
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Only system record exists
        ReplayMetaDataDTO systemRecord = new ReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.QUEUED.name());
        systemRecord.setTotalRecords(0L);
        systemRecord.setProcessedRecords(0L);
        systemRecord.setStartedAt(new Date());
        
        metadataList.add(systemRecord);
        
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Act
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        assertEquals("replay", response.getOperation());
        assertEquals(ReplayState.QUEUED.name(), response.getOverallState());
        assertEquals(0L, response.getTotalRecords().longValue());
        assertEquals(0L, response.getProcessedRecords().longValue());
        // No status entries for system record
        assertEquals(0, response.getStatus().size());
    }
    
    @Test
    public void testGetReplayStatus_WithMixedStates_ShouldCalculateCorrectOverallState() {
        // Arrange
        String replayId = "test-replay-id";
        
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // System record
        ReplayMetaDataDTO systemRecord = new ReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.IN_PROGRESS.name());
        systemRecord.setStartedAt(new Date());
        metadataList.add(systemRecord);
        
        // Completed kind
        ReplayMetaDataDTO completedKind = new ReplayMetaDataDTO();
        completedKind.setReplayId(replayId);
        completedKind.setKind("kind1");
        completedKind.setOperation("replay");
        completedKind.setState(ReplayState.COMPLETED.name());
        completedKind.setTotalRecords(100L);
        completedKind.setProcessedRecords(100L);
        completedKind.setStartedAt(new Date());
        metadataList.add(completedKind);
        
        // Failed kind
        ReplayMetaDataDTO failedKind = new ReplayMetaDataDTO();
        failedKind.setReplayId(replayId);
        failedKind.setKind("kind2");
        failedKind.setOperation("replay");
        failedKind.setState(ReplayState.FAILED.name());
        failedKind.setTotalRecords(50L);
        failedKind.setProcessedRecords(25L);
        failedKind.setStartedAt(new Date());
        metadataList.add(failedKind);
        
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Act
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        // Overall state should be FAILED because one kind failed
        assertEquals(ReplayState.FAILED.name(), response.getOverallState());
        assertEquals(150L, response.getTotalRecords().longValue());
        assertEquals(125L, response.getProcessedRecords().longValue());
        assertEquals(2, response.getStatus().size());
    }
    
    @Test(expected = AppException.class)
    public void testGetReplayStatus_WithNullReplayId_ShouldThrowException() {
        // Act
        replayService.getReplayStatus(null);
        // Should throw exception
    }
    
    @Test(expected = AppException.class)
    public void testGetReplayStatus_WithEmptyReplayId_ShouldThrowException() {
        // Act
        replayService.getReplayStatus("");
        // Should throw exception
    }
    
    @Test(expected = AppException.class)
    public void testGetReplayStatus_WithNonExistentReplayId_ShouldThrowException() {
        // Arrange
        String replayId = "non-existent-id";
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(Collections.emptyList());
        
        // Act
        replayService.getReplayStatus(replayId);
        // Should throw exception
    }
    
    @Test(expected = AppException.class)
    public void testHandleReplayRequest_WithNullRequest_ShouldThrowException() {
        // Act
        replayService.handleReplayRequest(null);
        // Should throw exception
    }
    
    @Test(expected = AppException.class)
    public void testHandleReplayRequest_WithInvalidOperation_ShouldThrowException() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setOperation("invalid-operation");
        
        // Act
        replayService.handleReplayRequest(request);
        // Should throw exception
    }
    
    @Test
    public void testHandleReplayRequest_WithNoReplayId_ShouldGenerateOne() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setOperation(ReplayOperation.REPLAY.name().toLowerCase());
        
        // Act
        ReplayResponse response = replayService.handleReplayRequest(request);
        
        // Assert
        assertNotNull(response.getReplayId());
        assertFalse(response.getReplayId().isEmpty());
    }
    
    @Test(expected = AppException.class)
    public void testHandleReplayRequest_WithInvalidKinds_ShouldThrowException() {
        // Arrange
        ReplayRequest request = new ReplayRequest();
        request.setReplayId("test-replay-id");
        request.setOperation(ReplayOperation.REPLAY.name().toLowerCase());
        
        ReplayFilter filter = new ReplayFilter();
        List<String> kinds = Arrays.asList("invalid-kind1", "invalid-kind2");
        filter.setKinds(kinds);
        request.setFilter(filter);
        
        // Mock the queryRepository to return no active records for the test kinds
        Map<String, Long> kindCounts = new HashMap<>();
        when(queryRepository.getActiveRecordsCountForKinds(kinds)).thenReturn(kindCounts);
        
        // Act
        replayService.handleReplayRequest(request);
        // Should throw exception
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testProcessReplayMessage_ShouldThrowUnsupportedOperationException() {
        // Arrange
        ReplayMessage message = new ReplayMessage();
        
        // Act
        replayService.processReplayMessage(message);
        // Should throw exception
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testProcessFailure_ShouldThrowUnsupportedOperationException() {
        // Arrange
        ReplayMessage message = new ReplayMessage();
        
        // Act
        replayService.processFailure(message);
        // Should throw exception
    }
    
    @Test
    public void testCalculateOverallState_AllCompleted_ShouldReturnCompleted() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        metadata2.setState(ReplayState.COMPLETED.name());
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        // Act
        // Call the private method using reflection
        ReplayState result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateOverallState", List.class);
            method.setAccessible(true);
            result = (ReplayState) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals(ReplayState.COMPLETED, result);
    }
    
    @Test
    public void testCalculateOverallState_WithFailedState_ShouldReturnFailed() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        metadata2.setState(ReplayState.FAILED.name());
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        // Act
        // Call the private method using reflection
        ReplayState result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateOverallState", List.class);
            method.setAccessible(true);
            result = (ReplayState) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals(ReplayState.FAILED, result);
    }
    
    @Test
    public void testCalculateOverallState_WithInProgressState_ShouldReturnInProgress() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        metadata2.setState(ReplayState.IN_PROGRESS.name());
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        // Act
        // Call the private method using reflection
        ReplayState result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateOverallState", List.class);
            method.setAccessible(true);
            result = (ReplayState) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals(ReplayState.IN_PROGRESS, result);
    }
    
    @Test
    public void testCreateInitialStatusRecord_ShouldSaveSystemRecord() {
        // Arrange
        String replayId = "test-replay-id";
        String operation = "replay";
        ReplayFilter filter = new ReplayFilter();
        filter.setKinds(Arrays.asList("kind1", "kind2"));
        
        ArgumentCaptor<ReplayMetaDataDTO> metadataCaptor = ArgumentCaptor.forClass(ReplayMetaDataDTO.class);
        
        // Act
        // Call the private method using reflection
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "createInitialStatusRecord", String.class, String.class, ReplayFilter.class);
            method.setAccessible(true);
            method.invoke(replayService, replayId, operation, filter);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        verify(replayRepository).save(metadataCaptor.capture());
        
        ReplayMetaDataDTO capturedMetadata = metadataCaptor.getValue();
        assertEquals("system", capturedMetadata.getId());
        assertEquals(replayId, capturedMetadata.getReplayId());
        assertEquals("system", capturedMetadata.getKind());
        assertEquals(operation, capturedMetadata.getOperation());
        assertEquals(ReplayState.QUEUED.name(), capturedMetadata.getState());
        assertEquals(0L, capturedMetadata.getTotalRecords().longValue());
        assertEquals(0L, capturedMetadata.getProcessedRecords().longValue());
        assertNotNull(capturedMetadata.getStartedAt());
        assertEquals(filter, capturedMetadata.getFilter());
    }
}
