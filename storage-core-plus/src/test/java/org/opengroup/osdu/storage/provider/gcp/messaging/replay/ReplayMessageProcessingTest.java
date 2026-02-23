/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ReplayMessageProcessingTest {

    private static final String TEST_PARTITION_ID_1 = "test-partition-1";
    private static final String TEST_PARTITION_ID_2 = "test-partition-2";
    private static final Long TEST_COMPLETION_COUNT_1 = 5L;
    private static final Long TEST_COMPLETION_COUNT_2 = 10L;

    @Mock
    private ReplayMessageService replayMessageService;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private OqmMessage oqmMessage;

    @Captor
    private ArgumentCaptor<String> headerKeyCaptor;

    @Captor
    private ArgumentCaptor<ReplayMessage> replayMessageCaptor;

    private ReplayMessageProcessing replayMessageProcessing;
    private Gson gson;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        replayMessageProcessing = new ReplayMessageProcessing(
                replayMessageService,
                dpsHeaders
        );
        gson = new Gson();

        // Attach ListAppender to capture log events in memory
        Logger logger = (Logger) LoggerFactory.getLogger(ReplayMessageProcessing.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReplayMessageProcessing.class);
        logger.detachAppender(logAppender);
    }

    // ==================== Constructor ====================

    @Test
    void constructor_shouldInitializeAllFieldsCorrectly() throws Exception {
        // Act
        ReplayMessageProcessing processing = new ReplayMessageProcessing(
                replayMessageService,
                dpsHeaders
        );

        // Assert - dependencies injected
        assertNotNull(processing);

        Field serviceField = ReplayMessageProcessing.class.getDeclaredField("replayMessageService");
        serviceField.setAccessible(true);
        assertSame(replayMessageService, serviceField.get(processing));

        Field headersField = ReplayMessageProcessing.class.getDeclaredField("dpsHeaders");
        headersField.setAccessible(true);
        assertSame(dpsHeaders, headersField.get(processing));

        // Assert - internal fields initialized
        Field gsonField = ReplayMessageProcessing.class.getDeclaredField("gson");
        gsonField.setAccessible(true);
        assertNotNull(gsonField.get(processing));

        Field listTypeField = ReplayMessageProcessing.class.getDeclaredField("listType");
        listTypeField.setAccessible(true);
        assertNotNull(listTypeField.get(processing));
    }

    // ==================== Single Message Processing ====================

    @Test
    void process_shouldHandleSingleMessageWithCompleteWorkflow() {
        // Arrange
        ReplayMessage replayMessage = createReplayMessage(TEST_PARTITION_ID_1, TEST_COMPLETION_COUNT_1);
        String json = gson.toJson(Collections.singletonList(replayMessage));
        when(oqmMessage.getData()).thenReturn(json);

        // Act
        replayMessageProcessing.process(oqmMessage);

        // Assert - data retrieved from OqmMessage
        verify(oqmMessage).getData();

        // Assert - headers set with correct values
        verify(dpsHeaders).put(DpsHeaders.DATA_PARTITION_ID, TEST_PARTITION_ID_1);
        verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());

        // Assert - processReplayMessage called with correct message
        verify(replayMessageService).processReplayMessage(replayMessageCaptor.capture());
        ReplayMessage captured = replayMessageCaptor.getValue();
        assertEquals(TEST_PARTITION_ID_1, captured.getDataPartitionId());
        assertEquals(TEST_COMPLETION_COUNT_1, captured.getBody().getCompletionCount());

        // Assert - debug log generated with completion count
        assertTrue(logAppender.list.stream()
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("Processing ReplayMessageProcessing") &&
                                event.getFormattedMessage().contains(String.valueOf(TEST_COMPLETION_COUNT_1))
                )
        );

        // Assert - headers set before processing
        InOrder inOrder = inOrder(dpsHeaders, replayMessageService);
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), any());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processReplayMessage(any(ReplayMessage.class));
    }

    // ==================== Multiple Messages Processing ====================

    @Test
    void process_shouldHandleMultipleMessagesInCorrectOrder() {
        // Arrange
        ReplayMessage message1 = createReplayMessage(TEST_PARTITION_ID_1, TEST_COMPLETION_COUNT_1);
        ReplayMessage message2 = createReplayMessage(TEST_PARTITION_ID_2, TEST_COMPLETION_COUNT_2);
        String json = gson.toJson(Arrays.asList(message1, message2));
        when(oqmMessage.getData()).thenReturn(json);

        // Act
        replayMessageProcessing.process(oqmMessage);

        // Assert - processReplayMessage called twice with correct data
        verify(replayMessageService, times(2)).processReplayMessage(replayMessageCaptor.capture());
        List<ReplayMessage> capturedMessages = replayMessageCaptor.getAllValues();
        assertEquals(2, capturedMessages.size());
        assertEquals(TEST_PARTITION_ID_1, capturedMessages.get(0).getDataPartitionId());
        assertEquals(TEST_PARTITION_ID_2, capturedMessages.get(1).getDataPartitionId());

        // Assert - headers set for both messages (2 headers per message = 4 total)
        verify(dpsHeaders, times(4)).put(headerKeyCaptor.capture(), any());
        List<String> capturedKeys = headerKeyCaptor.getAllValues();
        assertEquals(4, capturedKeys.size());
        assertTrue(capturedKeys.contains(DpsHeaders.DATA_PARTITION_ID));
        assertTrue(capturedKeys.contains(DpsHeaders.CORRELATION_ID));

        // Assert - two debug logs generated with different completion counts
        long debugCount = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .filter(event -> event.getFormattedMessage().contains("Processing ReplayMessageProcessing"))
                .count();
        assertEquals(2, debugCount);

        assertTrue(logAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(String.valueOf(TEST_COMPLETION_COUNT_1))));
        assertTrue(logAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(String.valueOf(TEST_COMPLETION_COUNT_2))));

        // Assert - correct processing order
        InOrder inOrder = inOrder(dpsHeaders, replayMessageService);
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), any());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processReplayMessage(any(ReplayMessage.class));
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), any());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processReplayMessage(any(ReplayMessage.class));
    }

    @Test
    void process_shouldStopOnFirstFailure() {
        // Arrange
        ReplayMessage message1 = createReplayMessage(TEST_PARTITION_ID_1, TEST_COMPLETION_COUNT_1);
        ReplayMessage message2 = createReplayMessage(TEST_PARTITION_ID_2, TEST_COMPLETION_COUNT_2);
        String json = gson.toJson(Arrays.asList(message1, message2));
        when(oqmMessage.getData()).thenReturn(json);

        doThrow(new RuntimeException("Processing error"))
                .when(replayMessageService).processReplayMessage(any(ReplayMessage.class));

        // Act & Assert - exception propagates, only first message attempted
        assertThrows(RuntimeException.class, () ->
                replayMessageProcessing.process(oqmMessage)
        );
        verify(replayMessageService, times(1)).processReplayMessage(any(ReplayMessage.class));
    }

    // ==================== Edge Cases ====================

    @Test
    void process_shouldHandleEdgeCases() {
        // Test 1: Empty message list - no processing
        String emptyJson = gson.toJson(Collections.emptyList());
        when(oqmMessage.getData()).thenReturn(emptyJson);
        replayMessageProcessing.process(oqmMessage);
        verify(replayMessageService, never()).processReplayMessage(any(ReplayMessage.class));
        verify(dpsHeaders, never()).put(any(), any());

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 2: Invalid JSON - throws exception
        when(oqmMessage.getData()).thenReturn("invalid-json");
        assertThrows(JsonSyntaxException.class, () ->
                replayMessageProcessing.process(oqmMessage)
        );

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 3: Null partition ID - processed normally
        ReplayMessage nullPartitionMessage = createReplayMessage(null, TEST_COMPLETION_COUNT_1);
        String nullPartitionJson = gson.toJson(Collections.singletonList(nullPartitionMessage));
        when(oqmMessage.getData()).thenReturn(nullPartitionJson);
        replayMessageProcessing.process(oqmMessage);
        verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), isNull());
        verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        verify(replayMessageService).processReplayMessage(any(ReplayMessage.class));

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 4: Empty string JSON - throws exception
        when(oqmMessage.getData()).thenReturn("");
        assertThrows(Exception.class, () ->
                replayMessageProcessing.process(oqmMessage)
        );

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 5: Null body - handles gracefully (may throw NPE in logging)
        ReplayMessage nullBodyMessage = new ReplayMessage();
        nullBodyMessage.setDataPartitionId(TEST_PARTITION_ID_1);
        nullBodyMessage.setBody(null);
        String nullBodyJson = gson.toJson(Collections.singletonList(nullBodyMessage));
        when(oqmMessage.getData()).thenReturn(nullBodyJson);

        // This will throw NPE when trying to log completion count
        assertThrows(NullPointerException.class, () ->
                replayMessageProcessing.process(oqmMessage)
        );
    }

    // ==================== Helper Methods ====================

    private ReplayMessage createReplayMessage(String partitionId, Long completionCount) {
        ReplayMessage message = new ReplayMessage();
        message.setDataPartitionId(partitionId);

        ReplayData replayData = new ReplayData();
        replayData.setCompletionCount(completionCount);
        message.setBody(replayData);

        return message;
    }
}
