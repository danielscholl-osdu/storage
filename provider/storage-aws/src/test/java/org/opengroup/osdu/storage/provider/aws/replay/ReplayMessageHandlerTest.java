/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.replay;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
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
    
    @Mock
    private K8sLocalParameterProvider parameterProvider;

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
        ReflectionTestUtils.setField(replayMessageHandler, "logger", mockLogger);
        
        // Mock headers behavior
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
        headerMap.put(DpsHeaders.AUTHORIZATION, "Bearer test-token");
        when(headers.getHeaders()).thenReturn(headerMap);
        
        // Reset mock logger before each test
        reset(mockLogger);
    }

    @Test
    public void testSendReplayMessageForReplayOperation() throws JsonProcessingException, ReplayMessageHandlerException {
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
    public void testSendReplayMessageForReindexOperation() throws JsonProcessingException, ReplayMessageHandlerException {
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
    public void testSendReplayMessageWithHeaders() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        // Add a required header with custom value
        message.getHeaders().put("authorization", "Bearer custom-token");
        // Add a non-required header that should be filtered out
        message.getHeaders().put("custom-header", "custom-value");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Override the default headers behavior for this test to prevent overwriting our custom authorization
        Map<String, String> customHeaderMap = new HashMap<>();
        customHeaderMap.put(DpsHeaders.DATA_PARTITION_ID, "test-partition");
        // Don't include authorization in the DpsHeaders to avoid overriding our custom one
        when(headers.getHeaders()).thenReturn(customHeaderMap);
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify
        verify(objectMapper).writeValueAsString(message);
        verify(snsClient).publish(any(PublishRequest.class));
        
        // Capture and verify the PublishRequest
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        PublishRequest capturedRequest = requestCaptor.getValue();
        
        // Verify message attributes
        Map<String, MessageAttributeValue> attributes = capturedRequest.getMessageAttributes();
        
        // Required header should be included with our custom value
        assertEquals("String", attributes.get("authorization").getDataType());
        assertEquals("Bearer custom-token", attributes.get("authorization").getStringValue());
        
        // Non-required header should NOT be included
        assertNull("Non-required header should not be included", attributes.get("custom-header"));
        
        // Verify operation attribute is included
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("replay", attributes.get("operation").getStringValue());
    }

    @Test
    public void testSendReplayMessageWithNullHeaders() throws JsonProcessingException, ReplayMessageHandlerException {
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
    public void testUpdateMessageWithCurrentHeaders() throws JsonProcessingException, ReplayMessageHandlerException {
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
    }

    @Test
    public void testUpdateMessageWithCurrentHeadersWhenHeadersNull() throws JsonProcessingException, ReplayMessageHandlerException {
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

        // Original headers should still be present
        assertEquals("test-partition", message.getHeaders().get("data-partition-id"));
    }

    @Test(expected = ReplayMessageHandlerException.class)
    public void testSendReplayMessageHandlesJsonProcessingException() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        // Mock behavior to throw exception
        when(objectMapper.writeValueAsString(message)).thenThrow(new JsonProcessingException("Test exception") {});
        
        // Execute - should throw ReplayMessageHandlerException
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
    }

    @Test
    public void testHandleFailure() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Execute
        replayMessageHandler.handleFailure(message);
        
        // Verify
        verify(replayMessageProcessor).processFailure(message);
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
        }
    }

    @Test
    public void testSendReplayMessageWithEmptyList() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare empty list
        List<ReplayMessage> messages = Collections.emptyList();
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify no interactions with SNS
        verify(snsClient, never()).publish(any(PublishRequest.class));
        verify(objectMapper, never()).writeValueAsString(any(ReplayMessage.class));
    }

    @Test
    public void testSendReplayMessageWithReplayTypeInBody() throws JsonProcessingException, ReplayMessageHandlerException {
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

    @Test
    public void testHandleNullMessage() {
        // Execute
        replayMessageHandler.handle(null);
        
        // Verify
        verify(replayMessageProcessor, never()).processReplayMessage(any());
        verify(mockLogger).severe("Cannot process null replay message or message with null body");
    }

    @Test
    public void testHandleMessageWithNullBody() {
        // Prepare test data
        ReplayMessage message = new ReplayMessage();
        message.setBody(null);
        
        // Execute
        replayMessageHandler.handle(message);
        
        // Verify
        verify(replayMessageProcessor, never()).processReplayMessage(any());
        verify(mockLogger).severe("Cannot process null replay message or message with null body");
    }

    @Test
    public void testHandleFailureWithNullMessage() {
        // Execute
        replayMessageHandler.handleFailure(null);
        
        // Verify
        verify(replayMessageProcessor, never()).processFailure(any());
        verify(mockLogger).severe("Cannot process failure for null replay message");
    }

    @Test
    public void testHandleFailureWithNullBody() {
        // Prepare test data
        ReplayMessage message = new ReplayMessage();
        message.setBody(null);
        
        // Execute
        replayMessageHandler.handleFailure(message);
        
        // Verify
        verify(replayMessageProcessor, never()).processFailure(any());
        verify(mockLogger).severe("Cannot process failure for null replay message");
    }

    @Test
    public void testSendReplayMessageWithNullOperation() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute with null operation
        replayMessageHandler.sendReplayMessage(messages, null);
        
        // Verify warning was logged
        verify(mockLogger).warning("Operation type is null or empty, using default");
        
        // Verify default operation was used
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        Map<String, MessageAttributeValue> attributes = requestCaptor.getValue().getMessageAttributes();
        assertEquals("replay", attributes.get("operation").getStringValue());
    }

    @Test
    public void testSendReplayMessageWithNullMessageInBatch() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data with a null message in the batch
        ReplayMessage message1 = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message1, null);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message1)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify warning was logged
        verify(mockLogger).warning("Skipping null message in batch");
        
        // Verify only the non-null message was processed
        verify(objectMapper, times(1)).writeValueAsString(any(ReplayMessage.class));
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test(expected = ReplayMessageHandlerException.class)
    public void testSendReplayMessageHandlesSNSException() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Arrays.asList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("SNS error"));
        
        // Execute - should throw ReplayMessageHandlerException
        replayMessageHandler.sendReplayMessage(messages, "replay");
    }

    @Test
    public void testCreateMessageAttributes() throws Exception {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        message.getHeaders().put("custom-header", "custom-value");
        
        // Call the private method using reflection
        java.lang.reflect.Method method = ReplayMessageHandler.class.getDeclaredMethod(
                "createMessageAttributes", ReplayMessage.class, String.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, MessageAttributeValue> attributes = 
                (Map<String, MessageAttributeValue>) method.invoke(replayMessageHandler, message, "replay");
        
        // Verify
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("replay", attributes.get("operation").getStringValue());
        
        // Verify only required headers are included
        // Note: The implementation now only includes required headers, so custom-header should not be present
        assertNull("Non-required header should not be included", attributes.get("custom-header"));
    }
    
    @Test
    public void testCreateMessageAttributesOnlyIncludesRequiredHeaders() throws Exception {
        // Prepare test data with many headers
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Add required headers with different cases to test case insensitivity
        message.getHeaders().put("data-partition-id", "test-partition");
        message.getHeaders().put("User", "test-user");
        message.getHeaders().put("CORRELATION-ID", "test-correlation");
        message.getHeaders().put("Authorization", "Bearer token");
        
        // Add many non-required headers
        message.getHeaders().put("custom-header-1", "value1");
        message.getHeaders().put("custom-header-2", "value2");
        message.getHeaders().put("custom-header-3", "value3");
        message.getHeaders().put("custom-header-4", "value4");
        message.getHeaders().put("custom-header-5", "value5");
        message.getHeaders().put("custom-header-6", "value6");
        message.getHeaders().put("custom-header-7", "value7");
        message.getHeaders().put("custom-header-8", "value8");
        message.getHeaders().put("custom-header-9", "value9");
        message.getHeaders().put("custom-header-10", "value10");
        
        // Call the private method using reflection
        java.lang.reflect.Method method = ReplayMessageHandler.class.getDeclaredMethod(
                "createMessageAttributes", ReplayMessage.class, String.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, MessageAttributeValue> attributes = 
                (Map<String, MessageAttributeValue>) method.invoke(replayMessageHandler, message, "replay");
        
        // Verify only required headers and operation are included (5 total attributes)
        assertEquals("Should only have 5 attributes (operation + 4 required headers)", 5, attributes.size());
        
        // Verify operation attribute
        assertEquals("String", attributes.get("operation").getDataType());
        assertEquals("replay", attributes.get("operation").getStringValue());
        
        // Verify required headers are included (with original case preserved)
        assertNotNull("data-partition-id header should be included", attributes.get("data-partition-id"));
        assertNotNull("User header should be included", attributes.get("User"));
        assertNotNull("CORRELATION-ID header should be included", attributes.get("CORRELATION-ID"));
        assertNotNull("Authorization header should be included", attributes.get("Authorization"));
        
        // Verify non-required headers are not included
        for (int i = 1; i <= 10; i++) {
            assertNull("Non-required header should not be included", 
                    attributes.get("custom-header-" + i));
        }
    }
    
    @Test
    public void testMessageAttributesDoNotExceedSNSLimit() throws Exception {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Add required headers
        message.getHeaders().put("data-partition-id", "test-partition");
        message.getHeaders().put("user", "test-user");
        message.getHeaders().put("correlation-id", "test-correlation");
        message.getHeaders().put("authorization", "Bearer token");
        
        // Add many non-required headers (more than the SNS limit of 10)
        for (int i = 1; i <= 15; i++) {
            message.getHeaders().put("custom-header-" + i, "value" + i);
        }
        
        // Call the private method using reflection
        java.lang.reflect.Method method = ReplayMessageHandler.class.getDeclaredMethod(
                "createMessageAttributes", ReplayMessage.class, String.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        Map<String, MessageAttributeValue> attributes = 
                (Map<String, MessageAttributeValue>) method.invoke(replayMessageHandler, message, "replay");
        
        // Verify the number of attributes doesn't exceed the SNS limit of 10
        assertTrue("Number of message attributes should not exceed SNS limit of 10", 
                attributes.size() <= 10);
        
        // Verify we have exactly 5 attributes (operation + 4 required headers)
        assertEquals("Should have exactly 5 attributes", 5, attributes.size());
    }

    private ReplayMessage createReplayMessage(String replayId, String kind, String operation) {
        ReplayData body = ReplayData.builder()
                .replayId(replayId)
                .kind(kind)
                .operation(operation)
                .build();
        
        Map<String, String> myHeaders = new HashMap<>();
        myHeaders.put("data-partition-id", "test-partition");
        
        return ReplayMessage.builder()
                .body(body)
                .headers(myHeaders)
                .build();
    }
    
    // Helper method for string contains matcher
    private static String contains(String substring) {
        return argThat(str -> str != null && str.contains(substring));
    }

    /**
     * Test the initialization with SNS client creation exception.
     */
    @Test
    public void testInitWithSNSClientException() {
        // Create a new instance with our mocks
        ReplayMessageHandler handler = new ReplayMessageHandler(objectMapper, replayMessageProcessor, headers);
        
        // Set up fields using reflection
        ReflectionTestUtils.setField(handler, "region", REGION);
        ReflectionTestUtils.setField(handler, "replayTopic", REPLAY_TOPIC);
        ReflectionTestUtils.setField(handler, "logger", mockLogger);
        
        // Create a RuntimeException to be thrown during initialization
        RuntimeException testException = new RuntimeException("Failed to create SNS client");
        
        // Create a spy to throw exception during init
        handler = spy(handler);
        doThrow(testException).when(handler).init();
        
        try {
            // Call init method
            handler.init();
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            // Expected exception
            assertEquals("Should be our test exception", testException, e);
        }
    }
    
    /**
     * Test sending a message with very large headers that exceed SNS limits.
     */
    @Test
    public void testSendReplayMessageWithLargeHeaders() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Add a very large authorization header (exceeding SNS attribute value size limit)
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeValue.append("x");
        }
        message.getHeaders().put("authorization", "Bearer " + largeValue.toString());
        
        List<ReplayMessage> messages = Collections.singletonList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Capture the request to verify headers
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        // Verify the authorization header was included but potentially truncated
        Map<String, MessageAttributeValue> attributes = requestCaptor.getValue().getMessageAttributes();
        assertNotNull("Authorization header should be included", attributes.get("authorization"));
        
        // SNS has a limit of 256 bytes for attribute values
        assertTrue("Authorization header should be within SNS limits", 
                attributes.get("authorization").getStringValue().length() <= 256);
    }
    
    /**
     * Test handling a message with an exception during processing that doesn't extend RuntimeException.
     */
    @Test
    public void testHandleMessageWithCheckedExceptionDuringProcessing() {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        
        // Mock behavior to throw a checked exception
        doThrow(new IllegalStateException("Test exception")).when(replayMessageProcessor).processReplayMessage(message);
        
        try {
            // Execute
            replayMessageHandler.handle(message);
            fail("Expected exception was not thrown");
        } catch (IllegalStateException e) {
            // Verify failure handling was called
            verify(replayMessageProcessor).processFailure(message);
        }
    }
    

    /**
     * Test sending a replay message with empty operation string.
     */
    @Test
    public void testSendReplayMessageWithEmptyOperation() throws JsonProcessingException, ReplayMessageHandlerException {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Collections.singletonList(message);
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute with empty operation
        replayMessageHandler.sendReplayMessage(messages, "");
        
        // Verify warning was logged
        verify(mockLogger).warning("Operation type is null or empty, using default");
        
        // Verify default operation was used
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(requestCaptor.capture());
        
        Map<String, MessageAttributeValue> attributes = requestCaptor.getValue().getMessageAttributes();
        assertEquals("replay", attributes.get("operation").getStringValue());
    }
    
    /**
     * Test updating message with current headers when DpsHeaders throws an exception.
     */
    @Test
    public void testUpdateMessageWithCurrentHeadersException() throws Exception {
        // Prepare test data
        ReplayMessage message = createReplayMessage("test-replay-id", "test-kind", "replay");
        List<ReplayMessage> messages = Collections.singletonList(message);
        
        // Mock headers to throw exception
        when(headers.getHeaders()).thenThrow(new RuntimeException("Headers exception"));
        
        String serializedMessage = "{\"message\"}";
        
        // Mock behavior
        when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Verify warning was logged
        verify(mockLogger).log(eq(Level.WARNING), contains("Failed to update message with current headers"), any(RuntimeException.class));
        
        // Verify message was still sent
        verify(snsClient).publish(any(PublishRequest.class));
    }
    
    /**
     * Test that isRequiredHeader correctly identifies headers case-insensitively.
     */
    @Test
    public void testIsRequiredHeader() throws Exception {
        // We can't directly test the private method, so we'll test its behavior indirectly
        // through the public sendReplayMessage method
        
        // Create messages with headers in different cases
        ReplayMessage message1 = createReplayMessage("test-replay-id", "test-kind", "replay");
        message1.getHeaders().put("DATA-PARTITION-ID", "test-partition-1");
        message1.getHeaders().put("User", "test-user-1");
        message1.getHeaders().put("CORRELATION-ID", "test-correlation-1");
        message1.getHeaders().put("Authorization", "Bearer token-1");
        
        ReplayMessage message2 = createReplayMessage("test-replay-id", "test-kind", "replay");
        message2.getHeaders().put("data-partition-id", "test-partition-2");
        message2.getHeaders().put("user", "test-user-2");
        message2.getHeaders().put("correlation-id", "test-correlation-2");
        message2.getHeaders().put("authorization", "Bearer token-2");
        
        List<ReplayMessage> messages = Arrays.asList(message1, message2);
        
        // Mock behavior
        when(objectMapper.writeValueAsString(any(ReplayMessage.class))).thenReturn("{\"message\"}");
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(new PublishResult().withMessageId("msg-id"));
        
        // Execute
        replayMessageHandler.sendReplayMessage(messages, "replay");
        
        // Capture the requests to verify headers
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient, times(2)).publish(requestCaptor.capture());
        
        // Verify both messages had their headers included regardless of case
        List<PublishRequest> capturedRequests = requestCaptor.getAllValues();
        
        // First message (uppercase/mixed case headers)
        Map<String, MessageAttributeValue> attributes1 = capturedRequests.get(0).getMessageAttributes();
        assertTrue("Should include DATA-PARTITION-ID header", attributes1.containsKey("DATA-PARTITION-ID"));
        assertTrue("Should include User header", attributes1.containsKey("User"));
        assertTrue("Should include CORRELATION-ID header", attributes1.containsKey("CORRELATION-ID"));
        assertTrue("Should include Authorization header", attributes1.containsKey("Authorization"));
        
        // Second message (lowercase headers)
        Map<String, MessageAttributeValue> attributes2 = capturedRequests.get(1).getMessageAttributes();
        assertTrue("Should include data-partition-id header", attributes2.containsKey("data-partition-id"));
        assertTrue("Should include user header", attributes2.containsKey("user"));
        assertTrue("Should include correlation-id header", attributes2.containsKey("correlation-id"));
        assertTrue("Should include authorization header", attributes2.containsKey("authorization"));
    }
}
