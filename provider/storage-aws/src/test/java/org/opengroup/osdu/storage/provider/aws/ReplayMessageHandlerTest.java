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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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

    private final String replayTopicArn = "arn:aws:sns:us-east-1:123456789012:replay-topic";
    private final String reindexTopicArn = "arn:aws:sns:us-east-1:123456789012:reindex-topic";
    private final String recordsTopicArn = "arn:aws:sns:us-east-1:123456789012:records-topic";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(replayMessageHandler, "replayTopicArn", replayTopicArn);
        ReflectionTestUtils.setField(replayMessageHandler, "reindexTopicArn", reindexTopicArn);
        ReflectionTestUtils.setField(replayMessageHandler, "recordsTopicArn", recordsTopicArn);
    }

    @Test
    public void testHandle() {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);

        // Act
        replayMessageHandler.handle(message);

        // Assert
        verify(replayService, times(1)).processReplayMessage(message);
    }

    @Test
    public void testHandleFailure() {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);

        // Act
        replayMessageHandler.handleFailure(message);

        // Assert
        verify(replayService, times(1)).processFailure(message);
    }

    @Test
    public void testSendReplayMessage_ReplayOperation() throws JsonProcessingException {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);
        
        List<ReplayMessage> messages = Arrays.asList(message);
        String operation = "replay";
        String messageJson = "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}";
        
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenReturn(messageJson);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("message-id"));

        // Act
        replayMessageHandler.sendReplayMessage(messages, operation);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(recordsTopicArn, capturedRequest.getTopicArn());
        assertEquals(messageJson, capturedRequest.getMessage());
    }

    @Test
    public void testSendReplayMessage_ReindexOperation() throws JsonProcessingException {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);
        
        List<ReplayMessage> messages = Arrays.asList(message);
        String operation = "reindex";
        String messageJson = "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}";
        
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenReturn(messageJson);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("message-id"));

        // Act
        replayMessageHandler.sendReplayMessage(messages, operation);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(reindexTopicArn, capturedRequest.getTopicArn());
        assertEquals(messageJson, capturedRequest.getMessage());
    }

    @Test
    public void testSendReplayMessage_DefaultOperation() throws JsonProcessingException {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);
        
        List<ReplayMessage> messages = Arrays.asList(message);
        String operation = "unknown";
        String messageJson = "{\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\"}}";
        
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenReturn(messageJson);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("message-id"));

        // Act
        replayMessageHandler.sendReplayMessage(messages, operation);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(replayTopicArn, capturedRequest.getTopicArn());
        assertEquals(messageJson, capturedRequest.getMessage());
    }

    @Test(expected = RuntimeException.class)
    public void testSendReplayMessage_JsonProcessingException() throws JsonProcessingException {
        // Arrange
        ReplayData body = new ReplayData();
        body.setReplayId("test-replay-id");
        body.setKind("test-kind");
        
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);
        
        List<ReplayMessage> messages = Arrays.asList(message);
        String operation = "replay";
        
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenThrow(new JsonProcessingException("Test exception") {});

        // Act
        replayMessageHandler.sendReplayMessage(messages, operation);

        // Assert - exception expected
    }
}
