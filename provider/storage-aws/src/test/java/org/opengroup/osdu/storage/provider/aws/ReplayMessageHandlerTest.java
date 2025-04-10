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

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayMessageHandlerTest {

    @Mock
    private AmazonSNS snsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ReplayMessageProcessorAWSImpl replayMessageProcessor;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private ReplayMessageHandler replayMessageHandler;

    private static final String REGION = "us-east-1";
    private static final String REPLAY_TOPIC = "replay-records";
    private static final String REPLAY_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:replay-records";
    private static final Logger mockLogger = mock(Logger.class);

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(replayMessageHandler, "region", REGION);
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopic", REPLAY_TOPIC);
        ReflectionTestUtils.setField(replayMessageHandler, "snsClient", snsClient);
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopicArn", REPLAY_TOPIC_ARN);
        ReflectionTestUtils.setField(replayMessageHandler, "LOGGER", mockLogger);
        
        // Mock headers behavior
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
        headerMap.put(DpsHeaders.AUTHORIZATION, "Bearer test-token");
        when(headers.getHeaders()).thenReturn(headerMap);
        
        // Reset mock logger before each test
        reset(mockLogger);
    }

    @Test
    public void testSendReplayMessageForReplayOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message1 = createReplayMessage("test-replay-id", "test-kind", "replay");
        ReplayMessage message2 = createReplayMessage("test-replay-id", "another-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message1, message2);
        
        String serializedMessage1 = "{\"message1\"}";
        String serializedMessage2 = "{\"message2\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message1)).thenReturn(serializedMessage1);
        when(objectMapper.writeValueAsString(message2)).thenReturn(serializedMessage2);
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(message1);
        verify(objectMapper).writeValueAsString(message2);
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        
        // Capture and verify the PublishRequest
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(2)).publish(requestCaptor.capture());
        
        // Verify the first request
        PublishRequest capturedRequest = requestCaptor.getAllValues().get(0);
        assertEquals(REPLAY_TOPIC_ARN, capturedRequest.getTopicArn());
        assertEquals(serializedMessage1, capturedRequest.getMessage());
        
        // Verify operation attribute
        Map<String, MessageAttributeValue> attributes = capturedRequest.getMessageAttributes();
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("replay", attributes.get("operation").getStringValue());
    }

    @Test
    public void testSendReplayMessageForReindexOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "reindex");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "reindex");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Capture and verify the PublishRequest
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(REPLAY_TOPIC_ARN, capturedRequest.getTopicArn());
        assertEquals(serializedMessage, capturedRequest.getMessage());
        
        // Verify operation attribute
        Map<String, MessageAttributeValue> attributes = capturedRequest.getMessageAttributes();
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("reindex", attributes.get("operation").getStringValue());
    }

    @Test
    public void testSendReplayMessageWithHeaders() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        message.getHeaders().put("custom-header", "custom-value");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Capture and verify the PublishRequest
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        
        // Verify headers were included as message attributes
        Map<String, MessageAttributeValue> attributes = capturedRequest.getMessageAttributes();
        assertEquals("String", attributes.get("custom-header").getDataType());
        assertEquals("custom-value", attributes.get("custom-header").getStringValue());
        
        // Verify DPS headers were added
        assertTrue(message.getHeaders().containsKey(DpsHeaders.DATA_PARTITION_ID));
        assertTrue(message.getHeaders().containsKey(DpsHeaders.AUTHORIZATION));
    }

    @Test
    public void testSendReplayMessageWithNullHeaders() throws JsonProcessingException {
        // Prepare test data with null headers
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        message.setHeaders(null);
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenReturn(serializedMessage);
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(any(ReplayMessage.class));
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Capture the message to verify headers were initialized
        ArgumentCaptor<ReplayMessage> messageCaptor = ArgumentCaptor.forClass(ReplayMessage.class);
        verify(objectMapper).writeValueAsString(messageCaptor.capture());
        
        // Headers should have been initialized
        assertNotNull(messageCaptor.getValue().getHeaders());
        
        // Verify operation attribute was still added
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        Map<String, MessageAttributeValue> attributes = requestCaptor.getValue().getMessageAttributes();
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("replay", attributes.get("operation").getStringValue());
    }

    @Test
    public void testUpdateMessageWithCurrentHeaders() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        message.getHeaders().clear(); // Start with empty headers
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify headers were updated from DpsHeaders
        assertEquals("test-partition", message.getHeaders().get(DpsHeaders.DATA_PARTITION_ID));
        assertEquals("Bearer test-token", message.getHeaders().get(DpsHeaders.AUTHORIZATION));
        
        // Verify correlation ID was added if not present
        assertNotNull(message.getHeaders().get(DpsHeaders.CORRELATION_ID));
        
        // Verify logging
        verify(mockLogger).info(contains("Updated message with current headers"));
    }

    @Test
    public void testUpdateMessageWithCurrentHeadersWhenHeadersNull() throws JsonProcessingException {
        // Mock DpsHeaders to return null
        when(headers.getHeaders()).thenReturn(null);
        
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify warning was logged
        verify(mockLogger).warning(contains("DpsHeaders is null or empty"));
        
        // Original headers should still be present
        assertEquals("test-partition", message.getHeaders().get("data-partition-id"));
    }

    @Test(expected = RuntimeException.class)
    public void testSendReplayMessageHandlesJsonProcessingException() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        // Mock behavior to throw exception
        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Test exception") {});
        
        // Execute - should throw RuntimeException
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify error was logged
        verify(mockLogger).log(eq(Level.SEVERE), contains("Failed to serialize replay message"), any(JsonProcessingException.class));
    }

    @Test
    public void testHandleMessage() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Execute
        replayMessageHandler.handle(message);
        
        // Verify
        verify(replayMessageProcessor).processReplayMessage(message);
        verify(mockLogger).info(contains("Processing replay message"));
    }

    @Test
    public void testHandleFailure() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Execute
        replayMessageHandler.handleFailure(message);
        
        // Verify
        verify(replayMessageProcessor).processFailure(message);
        
        // Verify logging - exactly once
        verify(mockLogger, times(1)).log(eq(Level.SEVERE), contains("Processing failure for replay message: test-replay-id for kind: test-kind"));
    }

    @Test
    public void testHandleMessageWithException() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Mock behavior to throw exception
        doThrow(new RuntimeException("Test exception")).when(replayMessageProcessor).processReplayMessage(message);
        
        try {
            // Execute
            replayMessageHandler.handle(message);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            // Verify
            verify(replayMessageProcessor).processReplayMessage(message);
            verify(replayMessageProcessor).processFailure(message);
            verify(mockLogger).log(eq(Level.SEVERE), contains("Error processing replay message"), any(RuntimeException.class));
        }
    }

    @Test
    public void testSendReplayMessageWithEmptyList() throws JsonProcessingException {
        // Prepare empty list
        List<ReplayMessage> messages = Collections.emptyList();
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify no interactions with SNS
        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(objectMapper, never()).writeValueAsString(any(ReplayMessage.class));
    }

    @Test
    public void testSendReplayMessageWithReplayTypeInBody() throws JsonProcessingException {
        // Prepare test data with ReplayType
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        message.getBody().setReplayType(ReplayType.REPLAY_KIND.name());
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Verify the message body contains the replay type
        assertEquals(ReplayType.REPLAY_KIND.name(), message.getBody().getReplayType());
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
    
    // Helper method for string contains matcher
    private static String contains(String substring) {
        return argThat(str -> str != null && str.contains(substring));
    }
}
