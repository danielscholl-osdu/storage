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
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageBusImplTest {

    @Mock
    private AmazonSNS snsClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private MessageBusImpl messageBus;

    private final String topicArn = "arn:aws:sns:us-east-1:123456789012:test-topic";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(messageBus, "currentRegion", "us-east-1");
        ReflectionTestUtils.setField(messageBus, "snsClient", snsClient);
        ReflectionTestUtils.setField(messageBus, "objectMapper", objectMapper);
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
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("message-id"));

        // Act
        messageBus.publishMessage(headers, routingInfo, messageList);

        // Assert
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(2)).publish(requestCaptor.capture());
        
        List<PublishRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(topicArn, capturedRequests.get(0).getTopicArn());
        assertEquals("{\"content\":\"test1\"}", capturedRequests.get(0).getMessage());
        assertEquals(topicArn, capturedRequests.get(1).getTopicArn());
        assertEquals("{\"content\":\"test2\"}", capturedRequests.get(1).getMessage());
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
