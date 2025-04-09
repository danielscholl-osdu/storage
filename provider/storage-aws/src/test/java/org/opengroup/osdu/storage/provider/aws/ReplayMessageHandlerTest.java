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
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
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
    private JaxRsDpsLog logger;

    @InjectMocks
    private ReplayMessageHandler replayMessageHandler;

    private static final String REGION = "us-east-1";
    private static final String REPLAY_TOPIC = "replay-records";
    private static final String REPLAY_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:replay-records";

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(replayMessageHandler, "region", REGION);
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopic", REPLAY_TOPIC);
        ReflectionTestUtils.setField(replayMessageHandler, "snsClient", snsClient);
        
        // Set topic ARN using reflection
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopicArn", REPLAY_TOPIC_ARN);
    }

    @Test
    public void testSendReplayMessageForReplayOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message1 = createReplayMessage("test-replay-id", "test-kind");
        ReplayMessage message2 = createReplayMessage("test-replay-id", "another-kind");
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
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
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
    public void testSendReplayMessageForDefaultOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "unknown");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Capture and verify the PublishRequest
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(REPLAY_TOPIC_ARN, capturedRequest.getTopicArn());
        
        // Verify operation attribute
        Map<String, MessageAttributeValue> attributes = capturedRequest.getMessageAttributes();
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("unknown", attributes.get("operation").getStringValue());
    }

    @Test(expected = RuntimeException.class)
    public void testSendReplayMessageHandlesJsonProcessingException() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        // Mock behavior to throw exception
        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Test exception") {});
        
        // Execute - should throw RuntimeException
        replayMessageHandler.sendReplayMessage(messages, "replay");
    }

    @Test
    public void testHandleMessage() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
        // Execute
        replayMessageHandler.handle(message);
        
        // Verify
        verify(replayMessageProcessor).processReplayMessage(message);
    }

    @Test
    public void testHandleFailure() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
        // Execute
        replayMessageHandler.handleFailure(message);
        
        // Verify
        verify(replayMessageProcessor).processFailure(message);
    }

    @Test
    public void testHandleMessageWithException() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
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
        }
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
