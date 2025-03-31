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
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private JaxRsDpsLog logger;

    @InjectMocks
    private ReplaySubscriptionMessageHandler messageHandler;

    private final String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/replay-queue";
    private final String receiptHandle = "receipt-handle";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(messageHandler, "replayQueueUrl", queueUrl);
    }

    @Test
    public void testPollMessages_Success() throws Exception {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setBody(body);
        
        // Create SNS message wrapper
        ObjectNode snsWrapper = objectMapper.createObjectNode();
        snsWrapper.put("Message", "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}");
        
        Message sqsMessage = new Message()
            .withBody(snsWrapper.toString())
            .withReceiptHandle(receiptHandle);
        
        ReceiveMessageResult receiveResult = new ReceiveMessageResult()
            .withMessages(Arrays.asList(sqsMessage));
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        sqsMessage.setAttributes(attributes);
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResult);
        when(objectMapper.readTree(anyString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);

        // Act
        messageHandler.pollMessages();

        // Assert
        verify(replayMessageHandler, times(1)).handle(any(ReplayMessage.class));
        verify(sqsClient, times(1)).deleteMessage(queueUrl, receiptHandle);
    }

    @Test
    public void testPollMessages_HandleException() throws Exception {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setBody(body);
        
        // Create SNS message wrapper
        ObjectNode snsWrapper = objectMapper.createObjectNode();
        snsWrapper.put("Message", "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}");
        
        Message sqsMessage = new Message()
            .withBody(snsWrapper.toString())
            .withReceiptHandle(receiptHandle);
        
        ReceiveMessageResult receiveResult = new ReceiveMessageResult()
            .withMessages(Arrays.asList(sqsMessage));
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        sqsMessage.setAttributes(attributes);
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResult);
        when(objectMapper.readTree(anyString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        doThrow(new RuntimeException("Test exception")).when(replayMessageHandler).handle(any(ReplayMessage.class));

        // Act
        messageHandler.pollMessages();

        // Assert
        verify(replayMessageHandler, times(1)).handle(any(ReplayMessage.class));
        verify(sqsClient, never()).deleteMessage(queueUrl, receiptHandle);
        verify(sqsClient, times(1)).changeMessageVisibility(eq(queueUrl), eq(receiptHandle), anyInt());
    }

    @Test
    public void testPollMessages_MaxDeliveryCount() throws Exception {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage replayMessage = new ReplayMessage();
        replayMessage.setBody(body);
        
        // Create SNS message wrapper
        ObjectNode snsWrapper = objectMapper.createObjectNode();
        snsWrapper.put("Message", "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}");
        
        Message sqsMessage = new Message()
            .withBody(snsWrapper.toString())
            .withReceiptHandle(receiptHandle);
        
        ReceiveMessageResult receiveResult = new ReceiveMessageResult()
            .withMessages(Arrays.asList(sqsMessage));
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "3"); // Max delivery count
        sqsMessage.setAttributes(attributes);
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResult);
        when(objectMapper.readTree(anyString())).thenReturn(snsWrapper);
        when(objectMapper.readValue(anyString(), eq(ReplayMessage.class))).thenReturn(replayMessage);
        doThrow(new RuntimeException("Test exception")).when(replayMessageHandler).handle(any(ReplayMessage.class));

        // Act
        messageHandler.pollMessages();

        // Assert
        verify(replayMessageHandler, times(1)).handle(any(ReplayMessage.class));
        verify(replayMessageHandler, times(1)).handleFailure(any(ReplayMessage.class));
        verify(sqsClient, times(1)).deleteMessage(queueUrl, receiptHandle);
    }

    @Test
    public void testPollMessages_ParseException() throws Exception {
        // Arrange
        Message sqsMessage = new Message()
            .withBody("invalid-json")
            .withReceiptHandle(receiptHandle);
        
        ReceiveMessageResult receiveResult = new ReceiveMessageResult()
            .withMessages(Arrays.asList(sqsMessage));
        
        Map<String, String> attributes = new HashMap<>();
        attributes.put("ApproximateReceiveCount", "1");
        sqsMessage.setAttributes(attributes);
        
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResult);
        when(objectMapper.readTree(anyString())).thenThrow(new RuntimeException("Invalid JSON"));

        // Act
        messageHandler.pollMessages();

        // Assert
        verify(replayMessageHandler, never()).handle(any(ReplayMessage.class));
        verify(sqsClient, times(1)).deleteMessage(queueUrl, receiptHandle);
        verify(logger, times(1)).error(contains("Failed to process message"), any(Exception.class));
    }

    private static String contains(String substring) {
        return argThat(str -> str != null && str.contains(substring));
    }
}
