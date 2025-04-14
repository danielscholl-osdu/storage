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

package org.opengroup.osdu.storage.provider.aws.replay;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplaySubscriptionMessageHandlerTest {

    @Mock
    private AmazonSQS sqsClient;

    @Mock
    private ReplayMessageHandler replayMessageHandler;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RequestScopeUtil requestScopeUtil;

    @InjectMocks
    private ReplaySubscriptionMessageHandler messageHandler;

    private static final String REGION = "us-east-1";
    private static final String REPLAY_TOPIC = "replay-records";
    private static final String REPLAY_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/replay-records-queue";
    // Define a constant for MAX_DELIVERY_COUNT that matches the implementation
    private static final int MAX_DELIVERY_COUNT = 3;

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(messageHandler, "region", REGION);
        ReflectionTestUtils.setField(messageHandler, "replayTopic", REPLAY_TOPIC);
        ReflectionTestUtils.setField(messageHandler, "sqsClient", sqsClient);
        
        // Set queue URL directly using reflection
        ReflectionTestUtils.setField(messageHandler, "replayQueueUrl", REPLAY_QUEUE_URL);
    }

    @Test
    public void testPollMessagesWithNoMessages() {
        // Mock behavior
        ReceiveMessageResult result = new ReceiveMessageResult().withMessages(Arrays.asList());
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(result);
        
        // Execute
        messageHandler.pollMessages();
        
        // Verify
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        verify(replayMessageHandler, never()).handle(any(ReplayMessage.class));
    }

    @Test
    public void testPollMessagesWithValidMessage() throws IOException {
        // Prepare test data
        String messageId = "test-message-id";
        String receiptHandle = "test-receipt-handle";
        String replayId = "test-replay-id";
        String kind = "test-kind";
        
        // Create SQS message
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        
        // Add message attributes with operation
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("operation", new MessageAttributeValue()
            .withDataType("String")
            .withStringValue("replay"));
        
        Message sqsMessage = new Message()
                .withMessageId(messageId)
                .withReceiptHandle(receiptHandle)
                .withAttributes(attributes)
                .withMessageAttributes(messageAttributes);
        
        // Create SNS wrapper
        ObjectNode snsWrapper = createSnsWrapper(replayId, kind);
        sqsMessage.setBody(snsWrapper.toString());
        
        // Create ReplayMessage
        ReplayMessage replayMessage = createReplayMessage(replayId, kind);
        
        // Mock behavior
        ReceiveMessageResult result = new ReceiveMessageResult().withMessages(Arrays.asList(sqsMessage));
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(result);
        when(objectMapper.readTree(snsWrapper.toString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        
        // Mock RequestScopeUtil to actually execute the runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
        
        // Execute
        messageHandler.pollMessages();
        
        // Verify
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        verify(replayMessageHandler).handle(any(ReplayMessage.class));
        verify(sqsClient).deleteMessage(REPLAY_QUEUE_URL, receiptHandle);
    }

    @Test
    public void testPollMessagesWithReindexOperation() throws IOException {
        // Prepare test data
        String messageId = "test-message-id";
        String receiptHandle = "test-receipt-handle";
        String replayId = "test-replay-id";
        String kind = "test-kind";
        
        // Create SQS message
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        
        // Add message attributes with operation
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("operation", new MessageAttributeValue()
            .withDataType("String")
            .withStringValue("reindex"));
        
        Message sqsMessage = new Message()
                .withMessageId(messageId)
                .withReceiptHandle(receiptHandle)
                .withAttributes(attributes)
                .withMessageAttributes(messageAttributes);
        
        // Create SNS wrapper
        ObjectNode snsWrapper = createSnsWrapper(replayId, kind);
        sqsMessage.setBody(snsWrapper.toString());
        
        // Create ReplayMessage
        ReplayMessage replayMessage = createReplayMessage(replayId, kind);
        
        // Mock behavior
        ReceiveMessageResult result = new ReceiveMessageResult().withMessages(Arrays.asList(sqsMessage));
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(result);
        when(objectMapper.readTree(snsWrapper.toString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        
        // Mock RequestScopeUtil to actually execute the runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
        
        // Execute
        messageHandler.pollMessages();
        
        // Verify
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        verify(replayMessageHandler).handle(any(ReplayMessage.class));
        verify(sqsClient).deleteMessage(REPLAY_QUEUE_URL, receiptHandle);
    }

    @Test
    public void testPollMessagesWithExceptionDuringProcessing() throws IOException {
        // Prepare test data
        String messageId = "test-message-id";
        String receiptHandle = "test-receipt-handle";
        String replayId = "test-replay-id";
        String kind = "test-kind";
        
        // Create SQS message
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        
        Message sqsMessage = new Message()
                .withMessageId(messageId)
                .withReceiptHandle(receiptHandle)
                .withAttributes(attributes);
        
        // Create SNS wrapper
        ObjectNode snsWrapper = createSnsWrapper(replayId, kind);
        sqsMessage.setBody(snsWrapper.toString());
        
        // Create ReplayMessage
        ReplayMessage replayMessage = createReplayMessage(replayId, kind);
        
        // Mock behavior
        ReceiveMessageResult result = new ReceiveMessageResult().withMessages(Arrays.asList(sqsMessage));
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(result);
        when(objectMapper.readTree(snsWrapper.toString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        doThrow(new RuntimeException("Test exception")).when(replayMessageHandler).handle(replayMessage);
        
        // Mock RequestScopeUtil to actually execute the runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
        
        // Execute
        messageHandler.pollMessages();
        
        // Verify
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        verify(replayMessageHandler).handle(replayMessage);
        verify(sqsClient).changeMessageVisibility(eq(REPLAY_QUEUE_URL), eq(receiptHandle), anyInt());
    }

    @Test
    public void testPollMessagesWithMaxDeliveryCountExceeded() throws IOException {
        // Prepare test data
        String messageId = "test-message-id";
        String receiptHandle = "test-receipt-handle";
        String replayId = "test-replay-id";
        String kind = "test-kind";
        
        // Create SQS message
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", String.valueOf(MAX_DELIVERY_COUNT));
        
        Message sqsMessage = new Message()
                .withMessageId(messageId)
                .withReceiptHandle(receiptHandle)
                .withAttributes(attributes);
        
        // Create SNS wrapper
        ObjectNode snsWrapper = createSnsWrapper(replayId, kind);
        sqsMessage.setBody(snsWrapper.toString());
        
        // Create ReplayMessage
        ReplayMessage replayMessage = createReplayMessage(replayId, kind);
        
        // Mock behavior
        ReceiveMessageResult result = new ReceiveMessageResult().withMessages(Arrays.asList(sqsMessage));
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(result);
        when(objectMapper.readTree(snsWrapper.toString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        doThrow(new RuntimeException("Test exception")).when(replayMessageHandler).handle(replayMessage);
        
        // Mock RequestScopeUtil to actually execute the runnable
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(requestScopeUtil).executeInRequestScope(any(Runnable.class), anyMap());
        
        // Execute
        messageHandler.pollMessages();
        
        // Verify
        verify(sqsClient).receiveMessage(any(ReceiveMessageRequest.class));
        verify(replayMessageHandler).handle(replayMessage);
        verify(replayMessageHandler).handleFailure(replayMessage);
        verify(sqsClient).deleteMessage(REPLAY_QUEUE_URL, receiptHandle);
    }

    private ObjectNode createSnsWrapper(String replayId, String kind) {
        ObjectNode snsWrapper = mock(ObjectNode.class);
        JsonNode messageNode = mock(JsonNode.class);
        
        when(snsWrapper.has("Message")).thenReturn(true);
        when(snsWrapper.get("Message")).thenReturn(messageNode);
        
        String messageContent = String.format("{\"body\":{\"replayId\":\"%s\",\"kind\":\"%s\",\"operation\":\"replay\"}}", replayId, kind);
        when(messageNode.asText()).thenReturn(messageContent);
        
        return snsWrapper;
    }

    private ReplayMessage createReplayMessage(String replayId, String kind) {
        ReplayData body = ReplayData.builder()
                .replayId(replayId)
                .kind(kind)
                .operation("replay")
                .build();
        
        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", "test-partition");
        
        return ReplayMessage.builder()
                .body(body)
                .headers(headers)
                .build();
    }
}
