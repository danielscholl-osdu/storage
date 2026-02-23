/*
 *  Copyright @ Microsoft Corporation
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
package org.opengroup.osdu.storage.provider.gcp.messaging.replay.deadlettering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.anyString;
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
import org.opengroup.osdu.storage.provider.gcp.messaging.replay.ReplayMessageService;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DeadLetteringMessageProcessingTest {

    private static final String TEST_PARTITION_ID_1 = "test-partition-1";
    private static final String TEST_PARTITION_ID_2 = "test-partition-2";
    private static final String TEST_BODY_1 = "test-body-1";
    private static final String TEST_BODY_2 = "test-body-2";

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

    private DeadLetteringMessageProcessing deadLetteringMessageProcessing;
    private Gson gson;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        deadLetteringMessageProcessing = new DeadLetteringMessageProcessing(
                replayMessageService,
                dpsHeaders
        );
        gson = new Gson();

        // Attach ListAppender to capture log events in memory
        Logger logger = (Logger) LoggerFactory.getLogger(DeadLetteringMessageProcessing.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(DeadLetteringMessageProcessing.class);
        logger.detachAppender(logAppender);
    }

    // ==================== Constructor ====================

    @Test
    void constructor_shouldInitializeAllFieldsCorrectly() throws Exception {
        // Act
        DeadLetteringMessageProcessing processing = new DeadLetteringMessageProcessing(
                replayMessageService,
                dpsHeaders
        );

        // Assert - dependencies injected
        assertNotNull(processing);

        Field serviceField = DeadLetteringMessageProcessing.class.getDeclaredField("replayMessageService");
        serviceField.setAccessible(true);
        assertSame(replayMessageService, serviceField.get(processing));

        Field headersField = DeadLetteringMessageProcessing.class.getDeclaredField("dpsHeaders");
        headersField.setAccessible(true);
        assertSame(dpsHeaders, headersField.get(processing));

        // Assert - internal fields initialized
        Field gsonField = DeadLetteringMessageProcessing.class.getDeclaredField("gson");
        gsonField.setAccessible(true);
        assertNotNull(gsonField.get(processing));

        Field listTypeField = DeadLetteringMessageProcessing.class.getDeclaredField("listType");
        listTypeField.setAccessible(true);
        assertNotNull(listTypeField.get(processing));
    }

    // ==================== Single Message Processing ====================

    @Test
    void process_shouldHandleSingleMessageWithCompleteWorkflow() {
        // Arrange
        ReplayMessage replayMessage = createReplayMessage(TEST_PARTITION_ID_1, TEST_BODY_1);
        String json = gson.toJson(Collections.singletonList(replayMessage));
        when(oqmMessage.getData()).thenReturn(json);

        // Act
        deadLetteringMessageProcessing.process(oqmMessage);

        // Assert - data retrieved from OqmMessage
        verify(oqmMessage).getData();

        // Assert - headers set with correct values
        verify(dpsHeaders).put(DpsHeaders.DATA_PARTITION_ID, TEST_PARTITION_ID_1);
        verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());

        // Assert - processFailure called with correct message
        verify(replayMessageService).processFailure(replayMessageCaptor.capture());
        ReplayMessage captured = replayMessageCaptor.getValue();
        assertEquals(TEST_PARTITION_ID_1, captured.getDataPartitionId());
        assertNotNull(captured.getBody());

        // Assert - debug log generated
        assertTrue(logAppender.list.stream()
                .anyMatch(event ->
                        event.getLevel() == Level.DEBUG &&
                                event.getFormattedMessage().contains("Dead Lettering: Message")
                )
        );

        // Assert - headers set before processing
        InOrder inOrder = inOrder(dpsHeaders, replayMessageService);
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), anyString());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processFailure(any(ReplayMessage.class));
    }

    // ==================== Multiple Messages Processing ====================

    @Test
    void process_shouldHandleMultipleMessagesInCorrectOrder() {
        // Arrange
        ReplayMessage message1 = createReplayMessage(TEST_PARTITION_ID_1, TEST_BODY_1);
        ReplayMessage message2 = createReplayMessage(TEST_PARTITION_ID_2, TEST_BODY_2);
        String json = gson.toJson(Arrays.asList(message1, message2));
        when(oqmMessage.getData()).thenReturn(json);

        // Act
        deadLetteringMessageProcessing.process(oqmMessage);

        // Assert - processFailure called twice with correct data
        verify(replayMessageService, times(2)).processFailure(replayMessageCaptor.capture());
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

        // Assert - two debug logs generated
        long debugCount = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .filter(event -> event.getFormattedMessage().contains("Dead Lettering: Message"))
                .count();
        assertEquals(2, debugCount);

        // Assert - correct processing order
        InOrder inOrder = inOrder(dpsHeaders, replayMessageService);
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), anyString());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processFailure(any(ReplayMessage.class));
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), anyString());
        inOrder.verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        inOrder.verify(replayMessageService).processFailure(any(ReplayMessage.class));
    }

    @Test
    void process_shouldStopOnFirstFailure() {
        // Arrange
        ReplayMessage message1 = createReplayMessage(TEST_PARTITION_ID_1, TEST_BODY_1);
        ReplayMessage message2 = createReplayMessage(TEST_PARTITION_ID_2, TEST_BODY_2);
        String json = gson.toJson(Arrays.asList(message1, message2));
        when(oqmMessage.getData()).thenReturn(json);

        doThrow(new RuntimeException("Processing error"))
                .when(replayMessageService).processFailure(any(ReplayMessage.class));

        // Act & Assert - exception propagates, only first message attempted
        assertThrows(RuntimeException.class, () ->
                deadLetteringMessageProcessing.process(oqmMessage)
        );
        verify(replayMessageService, times(1)).processFailure(any(ReplayMessage.class));
    }

    // ==================== Edge Cases ====================

    @Test
    void process_shouldHandleEdgeCases() {
        // Test 1: Empty message list - no processing
        String emptyJson = gson.toJson(Collections.emptyList());
        when(oqmMessage.getData()).thenReturn(emptyJson);
        deadLetteringMessageProcessing.process(oqmMessage);
        verify(replayMessageService, never()).processFailure(any(ReplayMessage.class));
        verify(dpsHeaders, never()).put(anyString(), any());

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 2: Invalid JSON - throws exception
        when(oqmMessage.getData()).thenReturn("invalid-json");
        assertThrows(JsonSyntaxException.class, () ->
                deadLetteringMessageProcessing.process(oqmMessage)
        );

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 3: Null message body - processed normally
        ReplayMessage nullBodyMessage = createReplayMessage(TEST_PARTITION_ID_1, null);
        String nullBodyJson = gson.toJson(Collections.singletonList(nullBodyMessage));
        when(oqmMessage.getData()).thenReturn(nullBodyJson);
        deadLetteringMessageProcessing.process(oqmMessage);
        verify(replayMessageService).processFailure(any(ReplayMessage.class));

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 4: Empty string JSON - throws exception
        when(oqmMessage.getData()).thenReturn("");
        assertThrows(Exception.class, () ->
                deadLetteringMessageProcessing.process(oqmMessage)
        );

        reset(oqmMessage, replayMessageService, dpsHeaders);
        logAppender.list.clear();

        // Test 5: Null fields in message - headers set to null
        ReplayMessage nullFieldsMessage = new ReplayMessage();
        nullFieldsMessage.setDataPartitionId(null);
        nullFieldsMessage.setBody(null);
        String nullFieldsJson = gson.toJson(Collections.singletonList(nullFieldsMessage));
        when(oqmMessage.getData()).thenReturn(nullFieldsJson);
        deadLetteringMessageProcessing.process(oqmMessage);
        verify(dpsHeaders).put(eq(DpsHeaders.DATA_PARTITION_ID), isNull());
        verify(dpsHeaders).put(eq(DpsHeaders.CORRELATION_ID), any());
        verify(replayMessageService).processFailure(any(ReplayMessage.class));
    }

    // ==================== Helper Methods ====================

    private ReplayMessage createReplayMessage(String partitionId, String bodyContent) {
        ReplayMessage message = new ReplayMessage();
        message.setDataPartitionId(partitionId);

        if (bodyContent != null) {
            ReplayData replayData = new ReplayData();
            message.setBody(replayData);
        }

        return message;
    }
}
