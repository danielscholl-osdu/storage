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
import org.opengroup.osdu.storage.provider.aws.replay.AwsReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.replay.ParallelReplayProcessor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.opengroup.osdu.storage.provider.aws.replay.ReplayRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayOperation;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
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
    private ReplayRepositoryImpl replayRepository;

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
        verify(replayRepository, times(3)).saveAwsReplayMetaData(any(AwsReplayMetaDataDTO.class));
        
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
        
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // First record
        AwsReplayMetaDataDTO metadata1 = new AwsReplayMetaDataDTO();
        metadata1.setReplayId(replayId);
        metadata1.setKind("kind1");
        metadata1.setOperation("replay");
        metadata1.setState(ReplayState.COMPLETED.name());
        metadata1.setTotalRecords(100L);
        metadata1.setProcessedRecords(100L);
        metadata1.setStartedAt(new Date());
        metadata1.setLastUpdatedAt(new Date());
        
        // Second record
        AwsReplayMetaDataDTO metadata2 = new AwsReplayMetaDataDTO();
        metadata2.setReplayId(replayId);
        metadata2.setKind("kind2");
        metadata2.setOperation("replay");
        metadata2.setState(ReplayState.IN_PROGRESS.name());
        metadata2.setTotalRecords(200L);
        metadata2.setProcessedRecords(50L);
        metadata2.setStartedAt(new Date());
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        when(((ReplayRepositoryImpl)replayRepository).getAwsReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Act
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        assertEquals("replay", response.getOperation());
        assertEquals(ReplayState.IN_PROGRESS.name(), response.getOverallState());
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
        
        ArgumentCaptor<AwsReplayMetaDataDTO> metadataCaptor = ArgumentCaptor.forClass(AwsReplayMetaDataDTO.class);
        
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
        verify(replayRepository, times(2)).saveAwsReplayMetaData(metadataCaptor.capture());
        
        List<AwsReplayMetaDataDTO> capturedMetadata = metadataCaptor.getAllValues();
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
        
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Only system record exists
        AwsReplayMetaDataDTO systemRecord = new AwsReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.QUEUED.name());
        systemRecord.setTotalRecords(0L);
        systemRecord.setProcessedRecords(0L);
        systemRecord.setStartedAt(new Date());
        
        metadataList.add(systemRecord);
        
        when(replayRepository.getAwsReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
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
        
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // System record
        AwsReplayMetaDataDTO systemRecord = new AwsReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.IN_PROGRESS.name());
        systemRecord.setStartedAt(new Date());
        metadataList.add(systemRecord);
        
        // Completed kind
        AwsReplayMetaDataDTO completedKind = new AwsReplayMetaDataDTO();
        completedKind.setReplayId(replayId);
        completedKind.setKind("kind1");
        completedKind.setOperation("replay");
        completedKind.setState(ReplayState.COMPLETED.name());
        completedKind.setTotalRecords(100L);
        completedKind.setProcessedRecords(100L);
        completedKind.setStartedAt(new Date());
        completedKind.setLastUpdatedAt(new Date());
        metadataList.add(completedKind);
        
        // Failed kind
        AwsReplayMetaDataDTO failedKind = new AwsReplayMetaDataDTO();
        failedKind.setReplayId(replayId);
        failedKind.setKind("kind2");
        failedKind.setOperation("replay");
        failedKind.setState(ReplayState.FAILED.name());
        failedKind.setTotalRecords(50L);
        failedKind.setProcessedRecords(25L);
        failedKind.setStartedAt(new Date());
        failedKind.setLastUpdatedAt(new Date());
        metadataList.add(failedKind);
        
        when(replayRepository.getAwsReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
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
        when(replayRepository.getAwsReplayStatusByReplayId(replayId)).thenReturn(Collections.emptyList());
        
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
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        AwsReplayMetaDataDTO metadata1 = new AwsReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        
        AwsReplayMetaDataDTO metadata2 = new AwsReplayMetaDataDTO();
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
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        AwsReplayMetaDataDTO metadata1 = new AwsReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        
        AwsReplayMetaDataDTO metadata2 = new AwsReplayMetaDataDTO();
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
    public void testCalculateEarliestStartTime_WithMultipleRecords_ShouldReturnEarliestTime() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Create records with different start times
        Calendar cal = Calendar.getInstance();
        
        // First record - middle time
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        cal.set(2023, Calendar.JANUARY, 15, 10, 0, 0);
        Date middleDate = cal.getTime();
        metadata1.setStartedAt(middleDate);
        
        // Second record - earliest time
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        cal.set(2023, Calendar.JANUARY, 10, 10, 0, 0);
        Date earliestDate = cal.getTime();
        metadata2.setStartedAt(earliestDate);
        
        // Third record - latest time
        ReplayMetaDataDTO metadata3 = new ReplayMetaDataDTO();
        metadata3.setKind("kind3");
        cal.set(2023, Calendar.JANUARY, 20, 10, 0, 0);
        Date latestDate = cal.getTime();
        metadata3.setStartedAt(latestDate);
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        metadataList.add(metadata3);
        
        // Act
        // Call the private method using reflection
        Date result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateEarliestStartTime", List.class);
            method.setAccessible(true);
            result = (Date) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals("Should return the earliest date", earliestDate, result);
    }
    
    @Test
    public void testCalculateEarliestStartTime_WithNullStartTimes_ShouldSkipNulls() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Create records with some null start times
        Calendar cal = Calendar.getInstance();
        
        // First record - null start time
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setStartedAt(null);
        
        // Second record - valid start time
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        cal.set(2023, Calendar.JANUARY, 10, 10, 0, 0);
        Date validDate = cal.getTime();
        metadata2.setStartedAt(validDate);
        
        // Third record - null start time
        ReplayMetaDataDTO metadata3 = new ReplayMetaDataDTO();
        metadata3.setKind("kind3");
        metadata3.setStartedAt(null);
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        metadataList.add(metadata3);
        
        // Act
        // Call the private method using reflection
        Date result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateEarliestStartTime", List.class);
            method.setAccessible(true);
            result = (Date) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals("Should return the only valid date", validDate, result);
    }
    
    @Test
    public void testCalculateEarliestStartTime_WithAllNullStartTimes_ShouldReturnCurrentTime() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Create records with all null start times
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setStartedAt(null);
        
        ReplayMetaDataDTO metadata2 = new ReplayMetaDataDTO();
        metadata2.setKind("kind2");
        metadata2.setStartedAt(null);
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        // Act
        // Call the private method using reflection
        Date result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateEarliestStartTime", List.class);
            method.setAccessible(true);
            result = (Date) method.invoke(replayService, metadataList);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertNotNull("Should return a non-null date", result);
        // The result should be close to the current time
        long currentTime = System.currentTimeMillis();
        long resultTime = result.getTime();
        assertTrue("Should return a time close to current time", 
                Math.abs(currentTime - resultTime) < 5000); // Within 5 seconds
    }
    
    @Test
    public void testCalculateElapsedTime_ForCompletedReplay_ShouldUseLatestCompletionTime() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Create records with different start and completion times
        Calendar cal = Calendar.getInstance();
        
        // First record - completed
        AwsReplayMetaDataDTO metadata1 = new AwsReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 0, 0);
        Date startDate1 = cal.getTime();
        metadata1.setStartedAt(startDate1);
        cal.set(2023, Calendar.JANUARY, 10, 11, 0, 0); // 1 hour later
        Date completionDate1 = cal.getTime();
        metadata1.setLastUpdatedAt(completionDate1);
        
        // Second record - completed later
        AwsReplayMetaDataDTO metadata2 = new AwsReplayMetaDataDTO();
        metadata2.setKind("kind2");
        metadata2.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 30, 0);
        Date startDate2 = cal.getTime();
        metadata2.setStartedAt(startDate2);
        cal.set(2023, Calendar.JANUARY, 10, 12, 0, 0); // 1.5 hours later
        Date completionDate2 = cal.getTime();
        metadata2.setLastUpdatedAt(completionDate2);
        
        metadataList.add(metadata1);
        metadataList.add(metadata2);
        
        // Act
        // Call the private method using reflection
        String result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateElapsedTime", List.class, ReplayState.class);
            method.setAccessible(true);
            result = (String) method.invoke(replayService, metadataList, ReplayState.COMPLETED);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertNotNull("Should return a non-null elapsed time string", result);
        // Expected format: "02:00:00" (2 hours)
        assertEquals("Should format elapsed time correctly", "02:00:00", result);
    }
    
    @Test
    public void testCalculateElapsedTime_ForInProgressReplay_ShouldUseCurrentTime() {
        // Arrange
        List<ReplayMetaDataDTO> metadataList = new ArrayList<>();
        
        // Create records with different start times
        Calendar cal = Calendar.getInstance();
        
        // Set start time to exactly 1 hour ago for easier testing
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, -1);
        Date oneHourAgo = cal.getTime();
        
        // First record - in progress
        ReplayMetaDataDTO metadata1 = new ReplayMetaDataDTO();
        metadata1.setKind("kind1");
        metadata1.setState(ReplayState.IN_PROGRESS.name());
        metadata1.setStartedAt(oneHourAgo);
        
        metadataList.add(metadata1);
        
        // Act
        // Call the private method using reflection
        String result = null;
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "calculateElapsedTime", List.class, ReplayState.class);
            method.setAccessible(true);
            result = (String) method.invoke(replayService, metadataList, ReplayState.IN_PROGRESS);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertNotNull("Should return a non-null elapsed time string", result);
        // The result should be close to "01:00:00" (1 hour) with some seconds
        assertTrue("Should format elapsed time correctly", result.startsWith("01:00:"));
    }
    
    @Test
    public void testFormatElapsedTime() {
        // Arrange
        long oneHour = 3600000; // 1 hour in milliseconds
        long oneMinute = 60000; // 1 minute in milliseconds
        long oneSecond = 1000; // 1 second in milliseconds
        
        // Act
        // Call the private method using reflection
        String result1 = null; // 1 hour, 30 minutes, 45 seconds
        String result2 = null; // 0 hours, 5 minutes, 10 seconds
        String result3 = null; // 25 hours, 15 minutes, 30 seconds
        
        try {
            java.lang.reflect.Method method = ReplayServiceAWSImpl.class.getDeclaredMethod(
                    "formatElapsedTime", long.class);
            method.setAccessible(true);
            result1 = (String) method.invoke(replayService, oneHour + 30 * oneMinute + 45 * oneSecond);
            result2 = (String) method.invoke(replayService, 5 * oneMinute + 10 * oneSecond);
            result3 = (String) method.invoke(replayService, 25 * oneHour + 15 * oneMinute + 30 * oneSecond);
        } catch (Exception e) {
            fail("Failed to call private method: " + e.getMessage());
        }
        
        // Assert
        assertEquals("Should format 1:30:45 correctly", "01:30:45", result1);
        assertEquals("Should format 0:05:10 correctly", "00:05:10", result2);
        assertEquals("Should format 25:15:30 correctly", "25:15:30", result3);
    }
    
    @Test
    public void testGetReplayStatus_ShouldUseCalculatedStartTimeAndElapsedTime() {
        // Arrange
        String replayId = "test-replay-id";
        
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        // System record
        AwsReplayMetaDataDTO systemRecord = new AwsReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.IN_PROGRESS.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 0, 0);
        systemRecord.setStartedAt(cal.getTime());
        systemRecord.setElapsedTime("00:10:00"); // This should be ignored
        metadataList.add(systemRecord);
        
        // First kind - earlier start time
        AwsReplayMetaDataDTO kind1 = new AwsReplayMetaDataDTO();
        kind1.setReplayId(replayId);
        kind1.setKind("kind1");
        kind1.setOperation("replay");
        kind1.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 9, 0, 0); // Earlier start
        Date earliestStart = cal.getTime();
        kind1.setStartedAt(earliestStart);
        cal.set(2023, Calendar.JANUARY, 10, 11, 0, 0);
        kind1.setLastUpdatedAt(cal.getTime());
        kind1.setElapsedTime("02:00:00"); // This should be ignored
        kind1.setTotalRecords(100L);
        kind1.setProcessedRecords(100L);
        metadataList.add(kind1);
        
        // Second kind - later start time
        AwsReplayMetaDataDTO kind2 = new AwsReplayMetaDataDTO();
        kind2.setReplayId(replayId);
        kind2.setKind("kind2");
        kind2.setOperation("replay");
        kind2.setState(ReplayState.IN_PROGRESS.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 30, 0); // Later start
        kind2.setStartedAt(cal.getTime());
        kind2.setElapsedTime("00:30:00"); // This should be ignored
        kind2.setTotalRecords(200L);
        kind2.setProcessedRecords(50L);
        metadataList.add(kind2);
        
        when(replayRepository.getAwsReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Mock the current time to make the test deterministic
        ReplayServiceAWSImpl spyService = spy(replayService);
        doReturn("01:30:00").when(spyService).calculateElapsedTime(any(), any());
        
        // Act
        ReplayStatusResponse response = spyService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        assertEquals("replay", response.getOperation());
        assertEquals(ReplayState.IN_PROGRESS.name(), response.getOverallState());
        assertEquals(300L, response.getTotalRecords().longValue());
        assertEquals(150L, response.getProcessedRecords().longValue());
        
        // Verify start time is the earliest one
        assertEquals("Should use earliest start time", earliestStart, response.getStartedAt());
        
        // Verify elapsed time is calculated and formatted correctly
        assertEquals("01:30:00", response.getElapsedTime());
    }
    
    @Test
    public void testGetReplayStatus_ForCompletedReplay_ShouldUseLatestCompletionForElapsedTime() {
        // Arrange
        String replayId = "test-replay-id";
        
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        // System record
        AwsReplayMetaDataDTO systemRecord = new AwsReplayMetaDataDTO();
        systemRecord.setReplayId(replayId);
        systemRecord.setKind("system");
        systemRecord.setOperation("replay");
        systemRecord.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 0, 0);
        systemRecord.setStartedAt(cal.getTime());
        systemRecord.setElapsedTime("00:10:00"); // This should be ignored
        metadataList.add(systemRecord);
        
        // First kind - earlier start time
        AwsReplayMetaDataDTO kind1 = new AwsReplayMetaDataDTO();
        kind1.setReplayId(replayId);
        kind1.setKind("kind1");
        kind1.setOperation("replay");
        kind1.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 9, 0, 0); // Earlier start
        Date earliestStart = cal.getTime();
        kind1.setStartedAt(earliestStart);
        cal.set(2023, Calendar.JANUARY, 10, 11, 0, 0);
        Date firstCompletion = cal.getTime();
        kind1.setLastUpdatedAt(firstCompletion);
        kind1.setElapsedTime("02:00:00"); // This should be ignored
        kind1.setTotalRecords(100L);
        kind1.setProcessedRecords(100L);
        metadataList.add(kind1);
        
        // Second kind - later start time but later completion
        AwsReplayMetaDataDTO kind2 = new AwsReplayMetaDataDTO();
        kind2.setReplayId(replayId);
        kind2.setKind("kind2");
        kind2.setOperation("replay");
        kind2.setState(ReplayState.COMPLETED.name());
        cal.set(2023, Calendar.JANUARY, 10, 10, 30, 0); // Later start
        kind2.setStartedAt(cal.getTime());
        cal.set(2023, Calendar.JANUARY, 10, 12, 0, 0); // Later completion
        Date latestCompletion = cal.getTime();
        kind2.setLastUpdatedAt(latestCompletion);
        kind2.setElapsedTime("01:30:00"); // This should be ignored
        kind2.setTotalRecords(200L);
        kind2.setProcessedRecords(200L);
        metadataList.add(kind2);
        
        when(replayRepository.getAwsReplayStatusByReplayId(replayId)).thenReturn(metadataList);
        
        // Act
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        
        // Assert
        assertEquals(replayId, response.getReplayId());
        assertEquals("replay", response.getOperation());
        assertEquals(ReplayState.COMPLETED.name(), response.getOverallState());
        assertEquals(300L, response.getTotalRecords().longValue());
        assertEquals(300L, response.getProcessedRecords().longValue());
        
        // Verify start time is the earliest one
        assertEquals("Should use earliest start time", earliestStart, response.getStartedAt());
        
        // Verify elapsed time is calculated from earliest start to latest completion
        // 9:00 to 12:00 = 3 hours
        assertEquals("Should calculate elapsed time from earliest start to latest completion", 
                "03:00:00", response.getElapsedTime());
    }
}
