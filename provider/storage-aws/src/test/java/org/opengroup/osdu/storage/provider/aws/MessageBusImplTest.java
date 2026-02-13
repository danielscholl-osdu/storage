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

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageBusImplTest {

    @Mock
    private SnsClient snsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private MessageBusImpl messageBus;

    private final String topicArn = "arn:aws:sns:us-east-1:123456789012:test-topic";
    private final String topicArnV2 = "arn:aws:sns:us-east-1:123456789012:test-topic-v2";
    private final String osduTopic = "records-changed";
    private final String osduTopicV2 = "records-changed-v2";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(messageBus, "currentRegion", "us-east-1");
        ReflectionTestUtils.setField(messageBus, "snsClient", snsClient);
        ReflectionTestUtils.setField(messageBus, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(messageBus, "amazonSNSTopic", topicArn);
        ReflectionTestUtils.setField(messageBus, "amazonSNSTopicV2", topicArnV2);
        ReflectionTestUtils.setField(messageBus, "osduStorageTopic", osduTopic);
        ReflectionTestUtils.setField(messageBus, "osduStorageTopicV2", osduTopicV2);
        
        when(headers.getCorrelationId()).thenReturn("test-correlation-id");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("message-id").build());
    }

    @Test
    public void testPublishMessage_WithPubSubInfo() {
        // Arrange
        PubSubInfo message1 = new PubSubInfo();
        message1.setId("id1");
        PubSubInfo message2 = new PubSubInfo();
        message2.setId("id2");
        
        // Act
        messageBus.publishMessage(headers, message1, message2);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(topicArnV2, capturedRequest.topicArn()); // AWS now uses V2 topic for all messages
        
        // Verify message attributes
        Map<String, MessageAttributeValue> attributes = capturedRequest.messageAttributes();
        assertTrue(attributes.containsKey("data-partition-id"));
        assertTrue(attributes.containsKey("correlation-id"));
    }

    @Test
    public void testPublishMessage_WithRecordChangedV2_NoCollaboration() {
        // Arrange
        RecordChangedV2 message1 = new RecordChangedV2();
        message1.setId("id1");
        RecordChangedV2 message2 = new RecordChangedV2();
        message2.setId("id2");
        
        Optional<CollaborationContext> noCollaboration = Optional.empty();
        
        // Act
        messageBus.publishMessage(noCollaboration, headers, message1, message2);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(topicArnV2, capturedRequest.topicArn());
        
        // Verify message attributes
        Map<String, MessageAttributeValue> attributes = capturedRequest.messageAttributes();
        assertTrue(attributes.containsKey("data-partition-id"));
        assertTrue(attributes.containsKey("correlation-id"));
        // No collaboration attribute should be present
        assertTrue(!attributes.containsKey("collaboration"));
    }

    @Test
    public void testPublishMessage_WithRecordChangedV2_WithCollaboration() {
        // Arrange
        RecordChangedV2 message = new RecordChangedV2();
        message.setId("id1");
        
        CollaborationContext collaborationContext = new CollaborationContext();
        collaborationContext.setId(UUID.randomUUID());
        collaborationContext.setApplication("test-app");
        Optional<CollaborationContext> withCollaboration = Optional.of(collaborationContext);
        
        // Act
        messageBus.publishMessage(withCollaboration, headers, message);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(1)).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        assertEquals(topicArnV2, capturedRequest.topicArn());
        
        // Verify message attributes
        Map<String, MessageAttributeValue> attributes = capturedRequest.messageAttributes();

        assertTrue(attributes.containsKey("data-partition-id"));
        assertTrue(attributes.containsKey("correlation-id"));
        assertTrue(attributes.containsKey("x-collaboration"));
        assertEquals("String", attributes.get("x-collaboration").dataType());
        assertTrue(attributes.get("x-collaboration").stringValue().contains("id="));
        assertTrue(attributes.get("x-collaboration").stringValue().contains("application=test-app"));
    }

    @Test
    public void testPublishMessage_WithRoutingInfo() throws JsonProcessingException {
        // Arrange
        Map<String, String> routingInfo = new HashMap<>();
        routingInfo.put("topic", topicArn);
        
        TestMessage message1 = new TestMessage("test1");
        TestMessage message2 = new TestMessage("test2");
        List<TestMessage> messageList = Arrays.asList(message1, message2);
        
        when(objectMapper.writeValueAsString(message1)).thenReturn("{\"content\":\"test1\"}");
        when(objectMapper.writeValueAsString(message2)).thenReturn("{\"content\":\"test2\"}");

        // Act
        messageBus.publishMessage(headers, routingInfo, messageList);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(2)).publish(requestCaptor.capture());
        
        List<PublishRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(topicArn, capturedRequests.get(0).topicArn());
        assertEquals("{\"content\":\"test1\"}", capturedRequests.get(0).message());
        assertEquals(topicArn, capturedRequests.get(1).topicArn());
        assertEquals("{\"content\":\"test2\"}", capturedRequests.get(1).message());
    }

    @Test
    public void testPublishMessage_NoTopicArn() {
        // Arrange
        Map<String, String> routingInfo = new HashMap<>();
        // No topic ARN provided
        
        TestMessage message = new TestMessage("test");
        List<TestMessage> messageList = Arrays.asList(message);

        // Act
        messageBus.publishMessage(headers, routingInfo, messageList);

        // Assert
        verify(logger, times(1)).error(contains("No SNS topic ARN provided in routing info"));
        verify(snsClient, never()).publish(any(PublishRequest.class));
    }

    @Test(expected = RuntimeException.class)
    public void testPublishMessage_JsonProcessingException() throws JsonProcessingException {
        // Arrange
        Map<String, String> routingInfo = new HashMap<>();
        routingInfo.put("topic", topicArn);
        
        TestMessage message = new TestMessage("test");
        List<TestMessage> messageList = Arrays.asList(message);
        
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Test exception") {});

        // Act
        messageBus.publishMessage(headers, routingInfo, messageList);

        // Assert - exception expected
    }

    @Test
    public void testPublishMessage_WithLargeBatch() {
        // Arrange
        int messageCount = 101; // More than the batch size of 50
        RecordChangedV2[] messages = new RecordChangedV2[messageCount];
        for (int i = 0; i < messageCount; i++) {
            RecordChangedV2 message = new RecordChangedV2();
            message.setId("id" + i);
            messages[i] = message;
        }
        
        Optional<CollaborationContext> noCollaboration = Optional.empty();
        
        // Act
        messageBus.publishMessage(noCollaboration, headers, messages);

        // Assert
        // Should have 3 batches: 50 + 50 + 1
        verify(snsClient, times(3)).publish(any(PublishRequest.class));
    }

    @Test(expected = NotImplementedException.class)
    public void testPublishMessage_WithRoutingInfoAndPubSubInfo() {
        // Arrange
        Map<String, String> routingInfo = new HashMap<>();
        routingInfo.put("topic", topicArn);
        
        PubSubInfo message = new PubSubInfo();
        message.setId("id1");
        
        // Act
        messageBus.publishMessage(headers, routingInfo, message);
        
        // Assert - NotImplementedException expected
    }

    private static String contains(String substring) {
        return argThat(str -> str != null && str.contains(substring));
    }

    // Test message class
    private static class TestMessage {
        private String content;

        public TestMessage(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
