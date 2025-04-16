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
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
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
    private DynamoDBQueryHelperV2 dynamoDBQueryHelper;

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

        // Mock behavior for DynamoDBQueryHelperFactory - use specific parameter types
        when(dynamoDBQueryHelperFactory.getQueryHelperForPartition(any(DpsHeaders.class), anyString(), any()))
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
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(recordMetadata);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, atLeastOnce()).getAwsReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(dynamoDBQueryHelper, times(2)).loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString());
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
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(recordMetadata);

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
    public void testProcessFailurePreservesCursor() {
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
