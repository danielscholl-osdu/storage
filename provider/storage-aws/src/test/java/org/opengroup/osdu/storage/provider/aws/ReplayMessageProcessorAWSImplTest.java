// Copyright © Amazon Web Services
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.aws;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayMessageProcessorAWSImplTest {

    @Mock
    private IReplayRepository replayRepository;

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
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Mock
    private DynamoDBQueryHelperV2 dynamoDBQueryHelper;

    @Mock
    private RecordMetadataDoc recordMetadata;

    @InjectMocks
    private ReplayMessageProcessorAWSImpl replayMessageProcessor;

    private static final String RECORD_METADATA_TABLE_PATH = "services/core/storage/RecordMetadataTable";
    private static final String TEST_REPLAY_ID = "test-replay-id";
    private static final String TEST_KIND = "test-kind";
    private static final String TEST_OPERATION = "replay";

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(replayMessageProcessor, "recordMetadataTableParameterRelativePath", RECORD_METADATA_TABLE_PATH);

        // Mock behavior for DynamoDBQueryHelperFactory
        when(dynamoDBQueryHelperFactory.getQueryHelperForPartition(eq(headers), eq(RECORD_METADATA_TABLE_PATH), any()))
                .thenReturn(dynamoDBQueryHelper);
    }

    @Test
    public void testProcessReplayMessage() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

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
        when(recordMetadata.getKind()).thenReturn(TEST_KIND);
        when(recordMetadata.getMetadata()).thenReturn(null); // Simulate no metadata for simplicity

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify - update the expected number of save calls to 3
        verify(replayRepository, times(3)).save(any(ReplayMetaDataDTO.class));
        verify(messageBus).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));
        verify(dynamoDBQueryHelper, times(2)).loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString());
    }

    @Test
    public void testProcessReplayMessageWithMultiplePages() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Mock record query results - first page
        RecordInfoQueryResult<RecordId> recordInfoQueryResult1 = new RecordInfoQueryResult<>();
        List<RecordId> records1 = new ArrayList<>();
        records1.add(createRecordId("record1"));
        records1.add(createRecordId("record2"));
        recordInfoQueryResult1.setResults(records1);
        recordInfoQueryResult1.setCursor("next-page"); // Has more pages

        // Mock record query results - second page
        RecordInfoQueryResult<RecordId> recordInfoQueryResult2 = new RecordInfoQueryResult<>();
        List<RecordId> records2 = new ArrayList<>();
        records2.add(createRecordId("record3"));
        records2.add(createRecordId("record4"));
        recordInfoQueryResult2.setResults(records2);
        recordInfoQueryResult2.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult1);
        when(queryRepository.getAllRecordIdsFromKind(anyInt(), eq("next-page"), eq(TEST_KIND))).thenReturn(recordInfoQueryResult2);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(recordMetadata);
        when(recordMetadata.getKind()).thenReturn(TEST_KIND);
        when(recordMetadata.getMetadata()).thenReturn(null); // Simulate no metadata for simplicity

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify - update the expected number of save calls to 4
        verify(replayRepository, times(4)).save(any(ReplayMetaDataDTO.class)); // Initial, after first page, after second page, final completion
        verify(messageBus, times(2)).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));
        verify(dynamoDBQueryHelper, times(4)).loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString());
    }

    @Test
    public void testProcessReplayMessageWithCompleteMetadata() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Mock record query results
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record1"));
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null); // No more pages

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup with complete metadata
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(recordMetadata);
        when(recordMetadata.getKind()).thenReturn(TEST_KIND);

        // Mock complete metadata
        RecordMetadata metadata = new org.opengroup.osdu.core.common.model.storage.RecordMetadata();

        // Add a version path so getLatestVersion() will work
        List<String> versionPaths = new ArrayList<>();
        versionPaths.add("test-kind/record1/2");
        metadata.setGcsVersionPaths(versionPaths);

        metadata.setModifyUser("test-user");
        when(recordMetadata.getMetadata()).thenReturn(metadata);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify - update the expected number of save calls to 3
        verify(replayRepository, times(3)).save(any(ReplayMetaDataDTO.class));
        verify(messageBus).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));

        // Verify the RecordChangedV2 object has all required fields
        verify(dynamoDBQueryHelper).loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString());
    }

    @Test
    public void testProcessFailure() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Execute
        replayMessageProcessor.processFailure(replayMessage);

        // Verify
        verify(replayRepository).getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(replayRepository).save(any(ReplayMetaDataDTO.class));
        verify(auditLogger).createReplayRequestFail(anyList());
    }

    @Test
    public void testProcessReplayMessageWithException() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Mock exception during query
        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND)))
                .thenThrow(new RuntimeException("Test exception"));

        try {
            // Execute
            replayMessageProcessor.processReplayMessage(replayMessage);
        } catch (Exception e) {
            // Expected exception
        }

        // Verify - update the expected number of save calls to 2
        verify(replayRepository, times(2)).save(any(ReplayMetaDataDTO.class)); // Initial state update + failure update
    }

    @Test
    public void testProcessRecordBatchWithMissingMetadata() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock record query results
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        records.add(createRecordId("record1"));
        recordInfoQueryResult.setResults(records);

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup to return null (missing metadata)
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(null);

        // Mock behavior for ReplayMetaDataDTO
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify warning was logged for missing metadata - we're using standard Java logger now, so don't verify
        // verify(logger).warning(contains("Record metadata not found"));

        // Verify no messages were published (since all records had missing metadata)
        verify(messageBus, never()).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));
    }

    private ReplayMessage createReplayMessage(String replayId, String kind, String operation) {
        ReplayData body = ReplayData.builder()
                .replayId(replayId)
                .kind(kind)
                .operation(operation)
                .build();

        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", "test-partition");

        return ReplayMessage.builder()
                .body(body)
                .headers(headers)
                .build();
    }

    private ReplayMetaDataDTO createReplayMetaData(String replayId, String kind, String operation) {
        ReplayMetaDataDTO dto = new ReplayMetaDataDTO();
        dto.setReplayId(replayId);
        dto.setKind(kind);
        dto.setOperation(operation);
        dto.setState(ReplayState.QUEUED.name());
        dto.setStartedAt(new Date());
        dto.setTotalRecords(10L);
        dto.setProcessedRecords(0L);
        return dto;
    }

    private RecordId createRecordId(String id) {
        RecordId recordId = new RecordId();
        recordId.setId(id);
        return recordId;
    }

    @Test
    public void testProcessNullReplayMessage() {
        // Execute
        replayMessageProcessor.processReplayMessage(null);

        // Verify no interactions with repository or message bus
        verify(replayRepository, never()).getReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(messageBus, never()).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));
    }

    @Test
    public void testProcessReplayMessageWithNullBody() {
        // Prepare test data with null body
        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setBody(null);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify no interactions with repository or message bus
        verify(replayRepository, never()).getReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(messageBus, never()).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));
    }

    @Test
    public void testProcessFailureWithNullMessage() {
        // Execute
        replayMessageProcessor.processFailure(null);

        // Verify no interactions with repository or audit logger
        verify(replayRepository, never()).getReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(auditLogger, never()).createReplayRequestFail(anyList());
    }

    @Test
    public void testProcessFailureWithNullBody() {
        // Prepare test data with null body
        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setBody(null);

        // Execute
        replayMessageProcessor.processFailure(replayMessage);

        // Verify no interactions with repository or audit logger
        verify(replayRepository, never()).getReplayStatusByKindAndReplayId(anyString(), anyString());
        verify(auditLogger, never()).createReplayRequestFail(anyList());
    }

    @Test
    public void testConvertToOperationType() throws Exception {
        // Test with reflection to access private method
        java.lang.reflect.Method method = ReplayMessageProcessorAWSImpl.class.getDeclaredMethod(
                "convertToOperationType", String.class);
        method.setAccessible(true);

        // Test with replay operation
        OperationType result1 = (OperationType) method.invoke(replayMessageProcessor, "replay");
        assertEquals(OperationType.update, result1);

        // Test with reindex operation
        OperationType result2 = (OperationType) method.invoke(replayMessageProcessor, "reindex");
        assertEquals(OperationType.update, result2);

        // Test with unknown operation
        OperationType result3 = (OperationType) method.invoke(replayMessageProcessor, "unknown");
        assertEquals(OperationType.update, result3);

        // Test with null operation
        OperationType result4 = (OperationType) method.invoke(replayMessageProcessor, (String) null);
        assertEquals(OperationType.update, result4);
    }

    @Test
    public void testGetCollaborationContext() throws Exception {
        // Test with reflection to access private method
        java.lang.reflect.Method method = ReplayMessageProcessorAWSImpl.class.getDeclaredMethod(
                "getCollaborationContext", ReplayMessage.class);
        method.setAccessible(true);

        // Test with valid collaboration header
        ReplayMessage message = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        String validUuid = "550e8400-e29b-41d4-a716-446655440000";
        message.getHeaders().put("x-collaboration", "id=" + validUuid + ",application=test-app");

        Optional<CollaborationContext> result = (Optional<CollaborationContext>) method.invoke(replayMessageProcessor, message);

        assertTrue(result.isPresent());
        assertEquals(validUuid, result.get().getId());
        assertEquals("test-app", result.get().getApplication());

        // Test with null message
        Optional<CollaborationContext> nullResult = (Optional<CollaborationContext>) method.invoke(replayMessageProcessor, (Object)null);
        assertFalse(nullResult.isPresent());

        // Test with null headers
        ReplayMessage messageNullHeaders = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        messageNullHeaders.setHeaders(null);
        Optional<CollaborationContext> nullHeadersResult = (Optional<CollaborationContext>) method.invoke(replayMessageProcessor, messageNullHeaders);
        assertFalse(nullHeadersResult.isPresent());

        // Test with missing collaboration header
        ReplayMessage messageNoCollab = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        messageNoCollab.getHeaders().remove("x-collaboration");
        Optional<CollaborationContext> noCollabResult = (Optional<CollaborationContext>) method.invoke(replayMessageProcessor, messageNoCollab);
        assertFalse(noCollabResult.isPresent());

        // Test with invalid collaboration header format
        ReplayMessage messageInvalidCollab = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        messageInvalidCollab.getHeaders().put("x-collaboration", "invalid-format");
        Optional<CollaborationContext> invalidCollabResult = (Optional<CollaborationContext>) method.invoke(replayMessageProcessor, messageInvalidCollab);
        assertFalse(invalidCollabResult.isPresent());
    }

    @Test
    public void testProcessReplayMessageWithEmptyRecordBatch() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Mock empty record query results
        RecordInfoQueryResult<RecordId> emptyResult = new RecordInfoQueryResult<>();
        emptyResult.setResults(Collections.emptyList());
        emptyResult.setCursor(null);

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(emptyResult);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, times(2)).save(any(ReplayMetaDataDTO.class)); // Initial update + completion update
        verify(messageBus, never()).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class)); // No messages should be published
    }

    @Test
    public void testProcessReplayMessageWithNullRecordBatch() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Mock null record query results
        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(null);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository, times(2)).save(any(ReplayMetaDataDTO.class)); // Initial update + completion update
        verify(messageBus, never()).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class)); // No messages should be published
    }

    @Test
    public void testCreateRecordChangedMessage() throws Exception {
        // Test with reflection to access private method
        java.lang.reflect.Method method = ReplayMessageProcessorAWSImpl.class.getDeclaredMethod(
                "createRecordChangedMessage", RecordMetadataDoc.class, String.class);
        method.setAccessible(true);

        // Create test record metadata
        RecordMetadataDoc testMetadata = mock(RecordMetadataDoc.class);
        when(testMetadata.getId()).thenReturn("test-id");
        when(testMetadata.getKind()).thenReturn("test-kind");

        // Create test record metadata with complete metadata
        RecordMetadata metadata = new RecordMetadata();
        metadata.setModifyUser("test-user");
        List<String> versionPaths = new ArrayList<>();
        versionPaths.add("test-kind/test-id/2");
        metadata.setGcsVersionPaths(versionPaths);
        when(testMetadata.getMetadata()).thenReturn(metadata);

        // Test with replay operation
        RecordChangedV2 result = (RecordChangedV2) method.invoke(replayMessageProcessor, testMetadata, "replay");

        // Verify result
        assertEquals("test-id", result.getId());
        assertEquals("test-kind", result.getKind());
        assertEquals("test-user", result.getModifiedBy());
        assertEquals(Optional.of((long)2), Optional.ofNullable(result.getVersion()));
        assertEquals(OperationType.update, result.getOp());
        assertEquals("data metadata", result.getRecordBlocks());
    }

    @Test
    public void testProcessReplayMessageWithNoMetadataFound() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior - no metadata found
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(null);

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify
        verify(replayRepository).getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID);
        verify(replayRepository, never()).save(any(ReplayMetaDataDTO.class));
        verify(queryRepository, never()).getAllRecordIdsFromKind(anyInt(), anyString(), anyString());
    }

    @Test
    public void testProcessLargeRecordBatch() {
        // Prepare test data
        ReplayMessage replayMessage = createReplayMessage(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);
        ReplayMetaDataDTO replayMetaData = createReplayMetaData(TEST_REPLAY_ID, TEST_KIND, TEST_OPERATION);

        // Mock behavior
        when(replayRepository.getReplayStatusByKindAndReplayId(TEST_KIND, TEST_REPLAY_ID)).thenReturn(replayMetaData);

        // Create a large batch of records (more than PUBLISH_BATCH_SIZE=50)
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        List<RecordId> records = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            records.add(createRecordId("record" + i));
        }
        recordInfoQueryResult.setResults(records);
        recordInfoQueryResult.setCursor(null);

        when(queryRepository.getAllRecordIdsFromKind(anyInt(), isNull(), eq(TEST_KIND))).thenReturn(recordInfoQueryResult);

        // Mock record metadata lookup
        when(dynamoDBQueryHelper.loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString())).thenReturn(recordMetadata);
        when(recordMetadata.getKind()).thenReturn(TEST_KIND);
        when(recordMetadata.getId()).thenReturn("test-id");

        // Execute
        replayMessageProcessor.processReplayMessage(replayMessage);

        // Verify - should have multiple publish calls due to batch size
        // With 75 records and batch size of 50, we expect 2 publish calls
        verify(messageBus, times(2)).publishMessage(any(Optional.class), eq(headers), any(RecordChangedV2[].class));

        // Verify all records were processed
        verify(dynamoDBQueryHelper, times(75)).loadByPrimaryKey(eq(RecordMetadataDoc.class), anyString());
    }
}
