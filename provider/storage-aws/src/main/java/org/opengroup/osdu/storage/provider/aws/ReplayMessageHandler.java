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
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
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
    private static Logger LOGGER = Logger.getLogger(ReplayMessageHandler.class.getName());
    
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
            try {
                K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
                replayTopicArn = provider.getParameterAsString(replayTopic + "-sns-topic-arn");
                LOGGER.info("Retrieved SNS topic ARN from SSM parameter: " + replayTopicArn);
            } catch (K8sParameterNotFoundException e) {
                // Fallback to default ARN for development
                LOGGER.warning("Failed to retrieve SNS topic ARN from SSM, using default value: " + e.getMessage());
                replayTopicArn = "arn:aws:sns:" + region + ":123456789012:" + replayTopic;
            }
            
            LOGGER.info("ReplayMessageHandler initialized with region: " + region);
        } catch (Exception e) {
            // Use standard Java logger for errors during initialization
            LOGGER.log(Level.SEVERE, "Failed to initialize ReplayMessageHandler: " + e.getMessage(), e);
        }
    }

    /**
     * Handles a replay message by processing it through the replay message processor.
     *
     * @param message The replay message to handle
     */
    public void handle(ReplayMessage message) {
        try {
            LOGGER.info("Processing replay message: " + message.getBody().getReplayId() + " for kind: " + message.getBody().getKind());
            // Process the replay message using the dedicated processor
            replayMessageProcessor.processReplayMessage(message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing replay message: " + e.getMessage(), e);
            // Handle failure
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
        LOGGER.log(Level.SEVERE, "Processing failure for replay message: " + message.getBody().getReplayId() + " for kind: " + message.getBody().getKind());
        replayMessageProcessor.processFailure(message);
    }
    
    /**
     * Sends replay messages to the consolidated SNS topic with operation-specific attributes.
     * This method uses a single topic for both replay and reindex operations, differentiating
     * them using message attributes.
     *
     * @param messages The replay messages to send
     * @param operation The operation type (e.g., "replay", "reindex")
     */
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        try {
            for (ReplayMessage message : messages) {
                // Ensure the message has all current headers
                updateMessageWithCurrentHeaders(message);
                
                String messageBody = objectMapper.writeValueAsString(message);

                // Create message attributes for SNS
                Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                
                // Add operation as a message attribute
                messageAttributes.put("operation", new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(operation));
                
                // Add all headers as message attributes for SNS
                if (message.getHeaders() != null) {
                    for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                        if (header.getValue() != null) {
                            messageAttributes.put(header.getKey(), new MessageAttributeValue()
                                .withDataType("String")
                                .withStringValue(header.getValue()));
                        }
                    }
                }

                PublishRequest publishRequest = new PublishRequest()
                    .withTopicArn(replayTopicArn)
                    .withMessage(messageBody)
                    .withMessageAttributes(messageAttributes);

                snsClient.publish(publishRequest);
                LOGGER.info("Published replay message to SNS topic for operation: " + operation + ", replayId: " + message.getBody().getReplayId());
            }
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize replay message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to serialize replay message", e);
        }
    }
    
    /**
     * Updates the message with all current headers from the request context.
     * This ensures that all necessary context information is preserved when the message is processed.
     *
     * @param message The replay message to update
     */
    private void updateMessageWithCurrentHeaders(ReplayMessage message) {
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
                
                LOGGER.info("Updated message with current headers: " + message.getHeaders());
            } else {
                LOGGER.warning("DpsHeaders is null or empty, unable to update message headers");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update message with current headers: " + e.getMessage(), e);
            // Continue without failing - we'll use the headers that were already in the message
        }
    }
}
