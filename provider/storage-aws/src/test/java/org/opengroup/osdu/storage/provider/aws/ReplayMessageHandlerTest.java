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
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    private ReplayService replayService;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private ReplayMessageHandler replayMessageHandler;

    private static final String REGION = "us-east-1";
    private static final String REPLAY_TOPIC = "replay-records";
    private static final String REINDEX_TOPIC = "reindex-records";
    private static final String RECORDS_TOPIC = "records-change";
    private static final String REPLAY_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:replay-records";
    private static final String REINDEX_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:reindex-records";
    private static final String RECORDS_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:records-change";

    @Before
    public void setUp() {
        // Set up fields using reflection
        ReflectionTestUtils.setField(replayMessageHandler, "region", REGION);
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopic", REPLAY_TOPIC);
        ReflectionTestUtils.setField(replayMessageHandler, "reindexTopic", REINDEX_TOPIC);
        ReflectionTestUtils.setField(replayMessageHandler, "recordsTopic", RECORDS_TOPIC);
        ReflectionTestUtils.setField(replayMessageHandler, "snsClient", snsClient);
        
        // Set topic ARNs using reflection
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopicArn", REPLAY_TOPIC_ARN);
        ReflectionTestUtils.setField(replayMessageHandler, "reindexTopicArn", REINDEX_TOPIC_ARN);
        ReflectionTestUtils.setField(replayMessageHandler, "recordsTopicArn", RECORDS_TOPIC_ARN);
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
        
        PublishRequest publishRequest = new PublishRequest().withTopicArn(RECORDS_TOPIC_ARN).withMessage(serializedMessage1);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(message1);
        verify(objectMapper).writeValueAsString(message2);
        verify(snsClient, times(2)).publish(any(PublishRequest.class));
        verify(logger, times(2)).info(anyString());
    }

    @Test
    public void testSendReplayMessageForReindexOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        
        PublishRequest publishRequest = new PublishRequest().withTopicArn(REINDEX_TOPIC_ARN).withMessage(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "reindex");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        verify(logger).info(anyString());
    }

    @Test
    public void testSendReplayMessageForDefaultOperation() throws JsonProcessingException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        
        PublishRequest publishRequest = new PublishRequest().withTopicArn(REPLAY_TOPIC_ARN).withMessage(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "unknown");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        verify(logger).info(anyString());
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
        
        // Verify
        verify(logger).error(anyString(), any(JsonProcessingException.class));
    }

    @Test
    public void testHandleMessage() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
        // Execute
        replayMessageHandler.handle(message);
        
        // Verify
        verify(replayService).processReplayMessage(message);
    }

    @Test
    public void testHandleFailure() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
        // Execute
        replayMessageHandler.handleFailure(message);
        
        // Verify
        verify(replayService).processFailure(message);
    }

    @Test
    public void testHandleMessageWithException() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind");
        
        // Mock behavior to throw exception
        doThrow(new RuntimeException("Test exception")).when(replayService).processReplayMessage(message);
        
        try {
            // Execute
            replayMessageHandler.handle(message);
            fail("Expected RuntimeException was not thrown");
        } catch (RuntimeException e) {
            // Verify
            verify(replayService).processReplayMessage(message);
            verify(logger).error(anyString(), any(RuntimeException.class));
            verify(replayService).processFailure(message);
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
