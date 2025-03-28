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
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplayMessageHandlerTest {

    @Mock
    private AmazonSQS sqsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ReplayService replayService;

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private ReplayMessageHandler messageHandler;

    private final String replayQueueUrl = "replay-queue-url";
    private final String reindexQueueUrl = "reindex-queue-url";
    private final String recordsQueueUrl = "records-queue-url";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(messageHandler, "replayQueueUrl", replayQueueUrl);
        ReflectionTestUtils.setField(messageHandler, "reindexQueueUrl", reindexQueueUrl);
        ReflectionTestUtils.setField(messageHandler, "recordsQueueUrl", recordsQueueUrl);
        
        // Mock SQS client to return a successful result
        SendMessageResult mockResult = new SendMessageResult();
        mockResult.setMessageId("test-message-id");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResult);
        
        // Override the constructor-created SQS client with our mock
        ReflectionTestUtils.setField(messageHandler, "sqsClient", sqsClient);
    }

    @Test
    public void testHandle() {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        // Execute
        messageHandler.handle(message);

        // Verify
        verify(replayService, times(1)).processReplayMessage(message);
        verify(replayService, never()).processFailure(any());
    }

    @Test
    public void testHandleWithException() {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        doThrow(new RuntimeException("Test exception")).when(replayService).processReplayMessage(message);

        // Execute and expect exception
        try {
            messageHandler.handle(message);
        } catch (Exception e) {
            // Expected
        }

        // Verify
        verify(replayService, times(1)).processReplayMessage(message);
        verify(replayService, times(1)).processFailure(message);
    }

    @Test
    public void testHandleFailure() {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        // Execute
        messageHandler.handleFailure(message);

        // Verify
        verify(replayService, times(1)).processFailure(message);
    }

    @Test
    public void testSendReplayMessage() throws JsonProcessingException {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        List<ReplayMessage> messages = Collections.singletonList(message);
        String operation = "replay";
        String serializedMessage = "{\"headers\":{\"data-partition-id\":\"test-partition\"},\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\",\"operation\":\"replay\"}}";

        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);

        // Execute
        messageHandler.sendReplayMessage(messages, operation);

        // Verify
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
        verify(objectMapper, times(1)).writeValueAsString(message);
    }

    @Test
    public void testSendReplayMessageMultiple() throws JsonProcessingException {
        // Setup
        ReplayMessage message1 = createReplayMessage("test-replay-id-1", "test-kind-1", "replay");
        ReplayMessage message2 = createReplayMessage("test-replay-id-2", "test-kind-2", "replay");

        List<ReplayMessage> messages = Arrays.asList(message1, message2);
        String operation = "replay";
        
        when(objectMapper.writeValueAsString(message1)).thenReturn("{\"message1\"}");
        when(objectMapper.writeValueAsString(message2)).thenReturn("{\"message2\"}");

        // Execute
        messageHandler.sendReplayMessage(messages, operation);

        // Verify
        verify(sqsClient, times(2)).sendMessage(any(SendMessageRequest.class));
        verify(objectMapper, times(1)).writeValueAsString(message1);
        verify(objectMapper, times(1)).writeValueAsString(message2);
    }

    @Test
    public void testSendReplayMessageDifferentOperations() throws JsonProcessingException {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        List<ReplayMessage> messages = Collections.singletonList(message);
        String serializedMessage = "{\"headers\":{\"data-partition-id\":\"test-partition\"},\"body\":{\"replayId\":\"test-replay-id\",\"kind\":\"test-kind\",\"operation\":\"replay\"}}";

        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);

        // Execute for different operations
        messageHandler.sendReplayMessage(messages, "replay");
        messageHandler.sendReplayMessage(messages, "reindex");
        messageHandler.sendReplayMessage(messages, "unknown");

        // Verify
        verify(sqsClient, times(3)).sendMessage(any(SendMessageRequest.class));
        verify(objectMapper, times(3)).writeValueAsString(message);
    }

    @Test(expected = RuntimeException.class)
    public void testSendReplayMessageJsonException() throws JsonProcessingException {
        // Setup
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");

        List<ReplayMessage> messages = Collections.singletonList(message);
        String operation = "replay";

        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Test exception") {});

        // Execute - should throw exception
        messageHandler.sendReplayMessage(messages, operation);
    }
    
    /**
     * Helper method to create a ReplayMessage with the current structure
     */
    private ReplayMessage createReplayMessage(String replayId, String kind, String operation) {
        // Create headers
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
        
        // Create body
        ReplayData body = ReplayData.builder()
            .replayId(replayId)
            .kind(kind)
            .operation(operation)
            .build();
        
        // Create message
        return ReplayMessage.builder()
            .headers(headers)
            .body(body)
            .build();
    }
}
