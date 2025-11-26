// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.google.gson.Gson;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.MessageBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.opengroup.osdu.storage.service.replay.IReplayService;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayMessageHandlerTest {

    @Mock
    private IReplayService replayService;

    @Mock
    private DpsHeaders headers;

    @Mock
    private MDCContextMap mdcContextMap;

    @Mock
    private IMessage message;

    @Mock
    private MessageBody messageBody;

    @InjectMocks
    private ReplayMessageHandler replayMessageHandler;

    private ReplayMessage testReplayMessage;
    private String testJsonMessage;

    @BeforeEach
    void setUp() {
        testReplayMessage = new ReplayMessage();
        testReplayMessage.setDataPartitionId("test-partition");
        testReplayMessage.getHeaders().put(DpsHeaders.CORRELATION_ID, "test-correlation-id");

        testJsonMessage = new Gson().toJson(testReplayMessage);

        lenient().when(message.getDeliveryCount()).thenReturn(1L);
        lenient().when(message.getMessageBody()).thenReturn(messageBody);

        Object mockValueData = new Object() {
            @Override
            public String toString() {
                return testJsonMessage;
            }
        };

        lenient().when(messageBody.getValueData()).thenReturn(mockValueData);

        Map<String, String> contextMap = new HashMap<>();
        contextMap.put("correlationId", "test-correlation-id");
        contextMap.put("partitionId", "test-partition");
        lenient().when(mdcContextMap.getContextMap("test-correlation-id", "test-partition")).thenReturn(contextMap);
    }

    @Test
    void testHandle_SuccessfulMessageProcessing() {
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(headers.getPartitionId()).thenReturn("test-partition");

        try (MockedStatic<MDC> mdcMockedStatic = mockStatic(MDC.class)) {
            replayMessageHandler.handle(message);

            verify(headers).put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
            verify(headers).put(DpsHeaders.CORRELATION_ID, "test-correlation-id");
            verify(replayService).processReplayMessage(any(ReplayMessage.class));
            mdcMockedStatic.verify(() -> MDC.setContextMap(any(Map.class)));
        }
    }

    @Test
    void testHandle_WithHighDeliveryCount() {
        when(message.getDeliveryCount()).thenReturn(5L);

        replayMessageHandler.handle(message);

        verify(message).getDeliveryCount();
        verify(replayService).processReplayMessage(any(ReplayMessage.class));
    }

    @Test
    void testHandle_WhenReplayServiceThrowsException() {
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(headers.getPartitionId()).thenReturn("test-partition");

        doThrow(new RuntimeException("Processing failed")).when(replayService).processReplayMessage(any(ReplayMessage.class));

        try (MockedStatic<MDC> mdcMockedStatic = mockStatic(MDC.class)) {
            assertThrows(RuntimeException.class, () -> replayMessageHandler.handle(message));
            verify(replayService).processReplayMessage(any(ReplayMessage.class));
        }
    }

    @Test
    void testHandle_InvalidJsonMessage() {
        Object invalidJsonData = new Object() {
            @Override
            public String toString() {
                return "invalid-json";
            }
        };

        when(messageBody.getValueData()).thenReturn(invalidJsonData);

        assertThrows(Exception.class, () -> replayMessageHandler.handle(message));
    }

    @Test
    void testHandleFailure_SuccessfulFailureProcessing() {
        replayMessageHandler.handleFailure(message);
        verify(replayService).processFailure(any(ReplayMessage.class));
        verify(message).getMessageBody();
    }

    @Test
    void testHandleFailure_WhenProcessFailureThrowsException() {
        doThrow(new RuntimeException("Failure processing failed")).when(replayService).processFailure(any(ReplayMessage.class));

        assertThrows(RuntimeException.class, () -> replayMessageHandler.handleFailure(message));
        verify(replayService, times(1)).processFailure(any(ReplayMessage.class));
    }

    @Test
    void testGetReplayMessage_ValidJson() {
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(headers.getPartitionId()).thenReturn("test-partition");

        try (MockedStatic<MDC> mdcMockedStatic = mockStatic(MDC.class)) {
            replayMessageHandler.handle(message);

            verify(replayService).processReplayMessage(argThat(replayMsg ->
                    "test-partition".equals(replayMsg.getDataPartitionId()) &&
                    "test-correlation-id".equals(replayMsg.getCorrelationId())
            ));
        }
    }

    @Test
    void testHandle_VerifyHeadersAreSetCorrectly() {
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(headers.getPartitionId()).thenReturn("test-partition");

        try (MockedStatic<MDC> mdcMockedStatic = mockStatic(MDC.class)) {
            replayMessageHandler.handle(message);

            verify(headers).put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
            verify(headers).put(DpsHeaders.CORRELATION_ID, "test-correlation-id");
            verify(mdcContextMap).getContextMap("test-correlation-id", "test-partition");
        }
    }

    @Test
    void testHandle_WithNullMessage() {
        assertThrows(NullPointerException.class, () -> replayMessageHandler.handle(null));
        verify(replayService, never()).processReplayMessage(any());
    }

    @Test
    void testHandleFailure_WithNullMessage() {
        assertThrows(NullPointerException.class, () -> replayMessageHandler.handleFailure(null));
        verify(replayService, never()).processFailure(any());
    }

    @Test
    void testHandle_VerifyMDCContextMapInteraction() {
        when(headers.getCorrelationId()).thenReturn("correlation-123");
        when(headers.getPartitionId()).thenReturn("partition-456");

        Map<String, String> expectedContextMap = new HashMap<>();
        expectedContextMap.put("test", "value");

        when(mdcContextMap.getContextMap("correlation-123", "partition-456")).thenReturn(expectedContextMap);

        try (MockedStatic<MDC> mdcMockedStatic = mockStatic(MDC.class)) {
            replayMessageHandler.handle(message);

            verify(mdcContextMap).getContextMap("correlation-123", "partition-456");
            mdcMockedStatic.verify(() -> MDC.setContextMap(expectedContextMap));
        }
    }

    @Test
    void testDependencyInjection() {
        assertNotNull(replayMessageHandler);
    }
}
