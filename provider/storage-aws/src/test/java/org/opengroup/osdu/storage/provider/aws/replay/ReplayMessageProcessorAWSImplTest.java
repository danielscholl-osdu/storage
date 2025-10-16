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
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayMessageProcessorAWSImplTest {

    @Mock
    private ReplayRepositoryImpl replayRepository;

    @Mock
    private QueryRepositoryImpl queryRepository;

    @Mock
    private IMessageBus messageBus;

    @Mock
    private DpsHeaders headers;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Mock
    private DynamoDBQueryHelper dynamoDBQueryHelper;

    @Mock
    private RecordMetadataDoc recordMetadata;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Captor
    private ArgumentCaptor<AwsReplayMetaDataDTO> replayMetaDataCaptor;

    @Captor
    private ArgumentCaptor<RecordChangedV2[]> recordChangedCaptor;

    @InjectMocks
    private ReplayMessageProcessorAWSImpl replayMessageProcessor;

    private static final String RECORD_METADATA_TABLE_PATH = "services/core/storage/RecordMetadataTable";
    private static final String TEST_REPLAY_ID = "test-replay-id";
    private static final String TEST_KIND = "test-kind";
    private static final String TEST_OPERATION = "replay";
    private static final String TEST_CURSOR = "test-cursor";

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(replayMessageProcessor, "recordMetadataTableParameterRelativePath", RECORD_METADATA_TABLE_PATH);
        
        // Set the batch sizes to ensure consistent test behavior
        ReflectionTestUtils.setField(replayMessageProcessor, "defaultBatchSize", 1000);
        ReflectionTestUtils.setField(replayMessageProcessor, "publishBatchSize", 100); // Set higher than test data to ensure single batch

        // Mock behavior for DynamoDBQueryHelperFactory - use specific parameter types
        when(dynamoDBQueryHelperFactory.createQueryHelper(any(DpsHeaders.class), anyString(), any()))
                .thenReturn(dynamoDBQueryHelper);
                
        // Mock headers
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("data-partition-id", "test-partition");
        
        // Mock record metadata with the fields needed by createRecordChangedMessage
        when(recordMetadata.getId()).thenReturn("test-id");
        when(recordMetadata.getKind()).thenReturn(TEST_KIND);
        
        // Create a mock for RecordMetadata that will be returned by recordMetadata.getMetadata()
        RecordMetadata mockMetadata = mock(RecordMetadata.class);
        when(mockMetadata.getLatestVersion()).thenReturn(1L);
        when(mockMetadata.getModifyUser()).thenReturn("test-user");
        when(recordMetadata.getMetadata()).thenReturn(mockMetadata);
    }

    @Test
    public void testProcessReplayMessage() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Mock record query results
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record1"));
        records.add(createRecordId("record2"));
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.getItem(anyString())).thenReturn(Optional.of(recordMetadata));

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(dynamoDBQueryHelper, times(2)).getItem(anyString());
        verify(messageBus, times(1)).publishMessage(any(), eq(headers), any(RecordChangedV2[].class));
        
        // Capture all saveAwsReplayMetaData calls
        verify(replayRepository, atLeastOnce()).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        
        // Check that at least one of the calls had COMPLETED state with null cursor
        List<AwsReplayMetaDataDTO> capturedValues = replayMetaDataCaptor.getAllValues();
        boolean foundCompletedState = false;
        for (AwsReplayMetaDataDTO dto : capturedValues) {
            if (ReplayState.COMPLETED.name().equals(dto.getState()) && dto.getLastCursor() == null) {
                foundCompletedState = true;
                break;
            }
        }
        assertTrue("No call with COMPLETED state and null cursor found", foundCompletedState);
    }

    @Test
    public void testProcessReplayMessageWithResume() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        awsReplayMetaData.setLastCursor(TEST_CURSOR);
        awsReplayMetaData.setProcessedRecords(5L);
        awsReplayMetaData.setLastUpdatedAt(new Date(System.currentTimeMillis() - 3600000)); // 1 hour ago

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Mock record query results - resuming from cursor
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record6"));
        records.add(createRecordId("record7"));
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), eq(TEST_CURSOR), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.getItem(anyString())).thenReturn(Optional.of(recordMetadata));

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(queryRepository).getAllRecordIdsFromKind(anyInt(), eq(TEST_CURSOR), eq(TEST_KIND));
        verify(messageBus, times(1)).publishMessage(any(), eq(headers), any(RecordChangedV2[].class));
        
        // Capture all saveAwsReplayMetaData calls
        verify(replayRepository, atLeastOnce()).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        
        // Check that at least one of the calls had COMPLETED state with null cursor and expected processed records
        List<AwsReplayMetaDataDTO> capturedValues = replayMetaDataCaptor.getAllValues();
        boolean foundCompletedState = false;
        for (AwsReplayMetaDataDTO dto : capturedValues) {
            if (ReplayState.COMPLETED.name().equals(dto.getState()) && 
                dto.getLastCursor() == null &&
                dto.getProcessedRecords() == 7L) {
                foundCompletedState = true;
                break;
            }
        }
        assertTrue("No call with COMPLETED state, null cursor, and 7 processed records found", foundCompletedState);
    }

    @Test
    public void testProcessReplayMessage_AlreadyCompleted() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        awsReplayMetaData.setState(ReplayState.COMPLETED.name());

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify that no further processing occurred
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(queryRepository, never()).getAllRecordIdsFromKind(anyInt(), any(), eq(TEST_KIND));
        verify(messageBus, never()).publishMessage(any(), any(), any(RecordChangedV2[].class));
        
        // Verify that the status was not updated
        verify(replayRepository, never()).saveAwsReplayMetaData(any(AwsReplayMetaDataDTO.class));
    }

    @Test
    public void testProcessReplayMessage_WithMultipleBatches() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Mock first batch of records
        RecordInfoQueryResult<RecordId> firstBatch = new RecordInfoQueryResult<>();
        List<RecordId> firstBatchRecords = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            firstBatchRecords.add(createRecordId("record" + i));
        }
        firstBatch.setResults(firstBatchRecords);
        firstBatch.setCursor("next-cursor"); // Has more pages

        // Mock second batch of records
        RecordInfoQueryResult<RecordId> secondBatch = new RecordInfoQueryResult<>();
        List<RecordId> secondBatchRecords = new ArrayList<>();
        for (int i = 5; i < 10; i++) {
            secondBatchRecords.add(createRecordId("record" + i));
        }
        secondBatch.setResults(secondBatchRecords);
        secondBatch.setCursor(null); // No more pages

        // Set up query repository to return different batches
        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(firstBatch);
        when(queryRepository.getAllRecordIdsFromKind(anyInt(), eq("next-cursor"), eq(TEST_KIND))).thenReturn(secondBatch);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.getItem(anyString())).thenReturn(Optional.of(recordMetadata));

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(queryRepository).getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND));
        verify(queryRepository).getAllRecordIdsFromKind(anyInt(), eq("next-cursor"), eq(TEST_KIND));
        
        // Verify that messages were published (could be multiple batches depending on PUBLISH_BATCH_SIZE)
        verify(messageBus, atLeastOnce()).publishMessage(any(), eq(headers), recordChangedCaptor.capture());
        
        // Verify that the final status update was to COMPLETED with null cursor
        verify(replayRepository, atLeastOnce()).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        List<AwsReplayMetaDataDTO> capturedValues = replayMetaDataCaptor.getAllValues();
        boolean foundCompletedState = false;
        for (AwsReplayMetaDataDTO dto : capturedValues) {
            if (ReplayState.COMPLETED.name().equals(dto.getState()) && dto.getLastCursor() == null) {
                foundCompletedState = true;
                assertEquals(10L, dto.getProcessedRecords().longValue());
                break;
            }
        }
        assertTrue("No call with COMPLETED state and null cursor found", foundCompletedState);
    }

    @Test
    public void testProcessReplayMessage_WithNullRecordMetadata() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Mock record query results
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record1"));
        records.add(createRecordId("record2"));
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup to return null for one record
        when(dynamoDBQueryHelper.getItem("record1")).thenReturn(Optional.of(recordMetadata));
        when(dynamoDBQueryHelper.getItem("record2")).thenReturn(Optional.empty());

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(dynamoDBQueryHelper, times(2)).getItem(anyString());
        
        // Verify that messages were still published for the valid record
        verify(messageBus, times(1)).publishMessage(any(), eq(headers), recordChangedCaptor.capture());
        
        // Verify that the final status was updated to COMPLETED
        verify(replayRepository, atLeastOnce()).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        List<AwsReplayMetaDataDTO> capturedValues = replayMetaDataCaptor.getAllValues();
        boolean foundCompletedState = false;
        for (AwsReplayMetaDataDTO dto : capturedValues) {
            if (ReplayState.COMPLETED.name().equals(dto.getState())) {
                foundCompletedState = true;
                break;
            }
        }
        assertTrue("No call with COMPLETED state found", foundCompletedState);
    }

    @Test
    public void testProcessReplayMessage_WithCollaborationContext() {
        // Prepare test data with collaboration header
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        String collaborationHeader = "id=550e8400-e29b-41d4-a716-446655440000,application=test-app";
        replayMessage.getHeaders().put("x-collaboration", collaborationHeader);
        
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Mock record query results
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record1"));
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.getItem(anyString())).thenReturn(Optional.of(recordMetadata));

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        
        // Verify that messages were published with collaboration context
        verify(messageBus, times(1)).publishMessage(argThat(opt -> opt.isPresent() && 
                                                          opt.get() instanceof CollaborationContext && 
                                                          "test-app".equals(((CollaborationContext)opt.get()).getApplication())), 
                                                   eq(headers), 
                                                   any(RecordChangedV2[].class));
    }

    @Test
    public void testProcessFailure() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        awsReplayMetaData.setLastCursor(TEST_CURSOR);
        awsReplayMetaData.setProcessedRecords(5L);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Execute
        replayMessageProcessor.processFailure(replayMessage);

        // Verify
        verify(replayRepository).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(replayRepository).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        verify(auditLogger).createReplayRequestFail(anyList());
        
        // Verify that the status was updated to FAILED but cursor was preserved
        AwsReplayMetaDataDTO capturedDto = replayMetaDataCaptor.getValue();
        assertEquals(ReplayState.FAILED.name(), capturedDto.getState());
        assertEquals(TEST_CURSOR, capturedDto.getLastCursor()); // Cursor should be preserved for resume
        assertNotNull(capturedDto.getLastUpdatedAt());
        assertNotNull(capturedDto.getElapsedTime());
    }

    @Test
    public void testProcessFailure_WithNullMessage() {
        // Execute with null message
        replayMessageProcessor.processFailure(null);
        
        // Verify no interactions with repository
        verify(replayRepository, never()).getAwsReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(replayRepository, never()).saveAwsReplayMetaData(any());
        verify(auditLogger, never()).createReplayRequestFail(anyList());
    }

    @Test
    public void testProcessFailure_WithNullBody() {
        // Create message with null body
        ReplayMessage replayMessage = ReplayMessage.builder()
                .headers(new HashMap<>())
                .build();
                
        // Execute
        replayMessageProcessor.processFailure(replayMessage);
        
        // Verify no interactions with repository
        verify(replayRepository, never()).getAwsReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(replayRepository, never()).saveAwsReplayMetaData(any());
        verify(auditLogger, never()).createReplayRequestFail(anyList());
    }

    @Test
    public void testUpdateReplayStatusToCompleted() {
        // Prepare test data
        AwsReplayMetaDataDTO awsReplayMetaData = createAwsReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        awsReplayMetaData.setLastCursor(TEST_CURSOR);
        awsReplayMetaData.setProcessedRecords(10L);

        // Mock behavior
        when(replayRepository.getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(awsReplayMetaData);

        // Execute using reflection to call private method
        ReflectionTestUtils.invokeMethod(
                replayMessageProcessor,
                "updateReplayStatusToCompleted",
                TEST_KIND, TEST_REPLAY_ID, 10L);

        // Verify
        verify(replayRepository).saveAwsReplayMetaData(replayMetaDataCaptor.capture());
        
        AwsReplayMetaDataDTO capturedDto = replayMetaDataCaptor.getValue();
        assertEquals(ReplayState.COMPLETED.name(), capturedDto.getState());
        assertNull(capturedDto.getLastCursor()); // Cursor should be cleared
        assertNotNull(capturedDto.getLastUpdatedAt());
        assertNotNull(capturedDto.getElapsedTime());
    }

    @Test
    public void testFormatElapsedTime() {
        // Test formatting with different time values
        String seconds = ReflectionTestUtils.invokeMethod(
                replayMessageProcessor,
                "formatElapsedTime",
                5000L); // 5 seconds
        assertEquals("5s", seconds);
        
        String minutesAndSeconds = ReflectionTestUtils.invokeMethod(
                replayMessageProcessor,
                "formatElapsedTime",
                125000L); // 2 minutes, 5 seconds
        assertEquals("2m 5s", minutesAndSeconds);
        
        String hoursMinutesAndSeconds = ReflectionTestUtils.invokeMethod(
                replayMessageProcessor,
                "formatElapsedTime",
                3725000L); // 1 hour, 2 minutes, 5 seconds
        assertEquals("1h 2m 5s", hoursMinutesAndSeconds);
    }

    /**
     * Helper method to create a test ReplayMessage
     */
    private ReplayMessage createReplayMessage(String replayId, String kind, String operation) {
        ReplayData body = ReplayData.builder()
                .replayId(replayId)
                .kind(kind)
                .operation(operation)
                .build();

        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("data-partition-id", "test-partition");

        return ReplayMessage.builder()
                .body(body)
                .headers(headersMap)
                .build();
    }

    /**
     * Helper method to create a test AwsReplayMetaDataDTO
     */
    private AwsReplayMetaDataDTO createAwsReplayMetaData(String replayId, String kind, String operation) {
        AwsReplayMetaDataDTO dto = new AwsReplayMetaDataDTO();
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation(operation);
        dto.setState(ReplayState.QUEUED.name());
        dto.setStartedAt(new Date());
        dto.setTotalRecords(10L);
        dto.setProcessedRecords(0L);
        return dto;
    }

    /**
     * Helper method to create a test RecordId
     */
    private RecordId createRecordId(String id) {
        RecordId recordId = new RecordId();
        recordId.setId(id);
        return recordId;
    }
}
