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
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for replay messages in AWS.
 * This class is responsible for sending and processing replay messages using SNS.
 * Uses a consolidated approach with a single SNS topic for all replay operations.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    // Use a standard Java logger for all logging
    // LOGGER is not final for unit testing
    private static Logger logger = Logger.getLogger(ReplayMessageHandler.class.getName());
    
    private static final String OPERATION_ATTRIBUTE = "operation";
    private static final String STRING_DATA_TYPE = "String";
    
    private AmazonSNS snsClient;
    
    private final ObjectMapper objectMapper;
    private final ReplayMessageProcessorAWSImpl replayMessageProcessor;
    private final DpsHeaders headers;
    
    @Value("${AWS.REGION:us-east-1}")
    private String region;
    
    @Value("${REPLAY_TOPIC:replay-records}")
    private String replayTopic;

    private String replayTopicArn;

    public ReplayMessageHandler(ObjectMapper objectMapper, ReplayMessageProcessorAWSImpl replayMessageProcessor, DpsHeaders headers) {
        this.objectMapper = objectMapper;
        this.replayMessageProcessor = replayMessageProcessor;
        this.headers = headers;
    }

    @PostConstruct
    public void init() {
        try {
            // Initialize SNS client
            snsClient = new AmazonSNSConfig(region).AmazonSNS();
            
            // For development, use simple topic ARNs
            // In production, these would be retrieved from SSM parameters
            K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
            replayTopicArn = provider.getParameterAsStringOrDefault(replayTopic + "-sns-topic-arn", "dummy-topic-to-prevent-runtime-failure");
        } catch (Exception e) {
            // Use standard Java logger for errors during initialization
            logger.log(Level.SEVERE, String.format("Failed to initialize ReplayMessageHandler: %s", e.getMessage()), e);
        }
    }

    /**
     * Handles a replay message by processing it through the replay message processor.
     *
     * @param message The replay message to handle
     */
    public void handle(ReplayMessage message) {
        if (message == null || message.getBody() == null) {
            logger.severe("Cannot process null replay message or message with null body");
            return;
        }

        try {
            // Process the replay message using the dedicated processor
            replayMessageProcessor.processReplayMessage(message);
        } catch (Exception e) {
            handleFailure(message);
            throw e;
        }
    }
    
    /**
     * Handles a failure in processing a replay message.
     *
     * @param message The replay message that failed
     */
    public void handleFailure(ReplayMessage message) {
        if (message == null || message.getBody() == null) {
            logger.severe("Cannot process failure for null replay message");
            return;
        }
        
        logger.log(Level.SEVERE, () -> String.format("Processing failure for replay message: %s for kind: %s",
            message.getBody().getReplayId(), message.getBody().getKind()));
        replayMessageProcessor.processFailure(message);
    }
    
    /**
     * Sends replay messages to the consolidated SNS topic with operation-specific attributes.
     * This method uses a single topic for both replay and reindex operations, differentiating
     * them using message attributes.
     *
     * @param messages The replay messages to send
     * @param operation The operation type (e.g., "replay", "reindex")
     * @throws ReplayMessageHandlerException if serialization or publishing fails
     */
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) throws ReplayMessageHandlerException {
        if (operation == null || operation.isEmpty()) {
            logger.warning("Operation type is null or empty, using default");
            operation = "replay";
        }

        try {
            for (ReplayMessage message : messages) {
                if (message == null) {
                    logger.warning("Skipping null message in batch");
                    continue;
                }
                
                // Ensure the message has all current headers
                updateMessageWithCurrentHeaders(message);
                publishMessageToSns(message, operation);
            }
        } catch (JsonProcessingException e) {
            throw new ReplayMessageHandlerException("Failed to serialize replay message", e);
        } catch (Exception e) {
            throw new ReplayMessageHandlerException("Failed to publish replay message to SNS", e);
        }
    }
    
    /**
     * Publishes a single message to SNS
     * 
     * @param message The message to publish
     * @param operation The operation type
     * @throws JsonProcessingException if serialization fails
     * @throws ReplayMessageHandlerException if publishing to SNS fails
     */
    private void publishMessageToSns(ReplayMessage message, String operation) throws JsonProcessingException, ReplayMessageHandlerException {
        String messageBody = objectMapper.writeValueAsString(message);
        Map<String, MessageAttributeValue> messageAttributes = createMessageAttributes(message, operation);

        try {
            PublishRequest publishRequest = new PublishRequest()
                .withTopicArn(replayTopicArn)
                .withMessage(messageBody)
                .withMessageAttributes(messageAttributes);

            snsClient.publish(publishRequest);
            
            if (message.getBody() != null) {
                logger.info(() -> String.format("Published replay message to SNS topic for operation: %s, replayId: %s",
                    operation, message.getBody().getReplayId()));
            }
        } catch (Exception e) {
            throw new ReplayMessageHandlerException("Failed to publish message to SNS topic: " + replayTopicArn, e);
        }
    }
    
    /**
     * Creates message attributes for SNS
     * Only includes required headers: data-partition-id, user, correlation-id, authorization
     * Headers are matched case-insensitively
     * 
     * @param message The replay message
     * @param operation The operation type
     * @return Map of message attributes
     */
    private Map<String, MessageAttributeValue> createMessageAttributes(ReplayMessage message, String operation) {
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        
        // Add operation as a message attribute
        messageAttributes.put(OPERATION_ATTRIBUTE, createStringAttribute(operation));
        
        // Add required headers if present
        addRequiredHeadersToAttributes(message, messageAttributes);
        
        return messageAttributes;
    }
    
    /**
     * Creates a String message attribute value
     * 
     * @param value The string value
     * @return MessageAttributeValue with String data type
     */
    private MessageAttributeValue createStringAttribute(String value) {
        return new MessageAttributeValue()
            .withDataType(STRING_DATA_TYPE)
            .withStringValue(value);
    }
    
    /**
     * Adds only the required headers from the message to the attributes map
     * 
     * @param message The message containing headers
     * @param attributes The attributes map to add to
     */
    private void addRequiredHeadersToAttributes(ReplayMessage message, Map<String, MessageAttributeValue> attributes) {
        if (message.getHeaders() == null) {
            return;
        }

        final String[] requiredHeaders = {
            "data-partition-id",
            "user",
            "correlation-id",
            "authorization"
        };

        for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
            if (header.getKey() != null && header.getValue() != null && isRequiredHeader(header.getKey(), requiredHeaders)) {
                attributes.put(header.getKey(), createStringAttribute(header.getValue()));
            }
        }
    }
    
    /**
     * Checks if a header is in the required headers list (case insensitive)
     * 
     * @param headerName The header name to check
     * @param requiredHeaders Array of required header names
     * @return true if the header is required
     */
    private boolean isRequiredHeader(String headerName, String[] requiredHeaders) {
        for (String required : requiredHeaders) {
            if (required.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Updates the message with all current headers from the request context.
     * This ensures that all necessary context information is preserved when the message is processed.
     *
     * @param message The replay message to update
     */
    private void updateMessageWithCurrentHeaders(ReplayMessage message) {
        if (message == null) {
            logger.warning("Cannot update headers for null message");
            return;
        }
        
        try {
            // Initialize headers if null
            if (message.getHeaders() == null) {
                message.setHeaders(new HashMap<>());
            }
            
            // Add all current headers to the message from the injected DpsHeaders
            if (headers != null && headers.getHeaders() != null) {
                message.getHeaders().putAll(headers.getHeaders());
                
                // Ensure critical headers are present
                if (message.getHeaders().get(DpsHeaders.CORRELATION_ID) == null) {
                    message.getHeaders().put(DpsHeaders.CORRELATION_ID, UUID.randomUUID().toString());
                }
                
                logger.fine(() -> String.format("Updated message with current headers: %s", message.getHeaders()));
            } else {
                logger.warning("DpsHeaders is null or empty, unable to update message headers");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, String.format("Failed to update message with current headers: %s", e.getMessage()), e);
            // Continue without failing - we'll use the headers that were already in the message
        }
    }
}
