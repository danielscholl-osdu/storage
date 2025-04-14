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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.sqs.AmazonSQSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQS message listener for replay messages.
 * This class polls the SQS queue for replay messages and processes them.
 * The messages are published to a single SNS topic but consumed from a single SQS queue.
 * The operation type (replay or reindex) is determined from message attributes.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
public class ReplaySubscriptionMessageHandler {
    public static final int MAX_DELIVERY_COUNT = 3;
    private static final Logger LOGGER = Logger.getLogger(ReplaySubscriptionMessageHandler.class.getName());
    
    private AmazonSQS sqsClient;
    
    private final ReplayMessageHandler replayMessageHandler;
    
    private final ObjectMapper objectMapper;
    
    private final RequestScopeUtil requestScopeUtil;

    @Value("${AWS.REGION:us-east-1}")
    private String region;
    
    @Value("${REPLAY_TOPIC:replay-records}")
    private String replayTopic;
    
    private String replayQueueUrl;

    public ReplaySubscriptionMessageHandler(ReplayMessageHandler replayMessageHandler, ObjectMapper objectMapper, RequestScopeUtil requestScopeUtil) {
        this.replayMessageHandler = replayMessageHandler;
        this.objectMapper = objectMapper;
        this.requestScopeUtil = requestScopeUtil;
    }

    @PostConstruct
    public void init() {
        try {
            // Initialize SQS client
            AmazonSQSConfig sqsConfig = new AmazonSQSConfig(region);
            this.sqsClient = sqsConfig.AmazonSQS();
            
            // Try to get queue URL from SSM parameters
            try {
                K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
                replayQueueUrl = provider.getParameterAsString(replayTopic + "-sqs-queue-url");
                LOGGER.info(() -> String.format("Retrieved SQS queue URL from SSM: %s", replayQueueUrl));
            } catch (K8sParameterNotFoundException e) {
                // For development, use a default queue URL
                LOGGER.warning("Failed to retrieve SQS queue URL from SSM, using default value: " + e.getMessage());
                replayQueueUrl = "https://sqs." + region + ".amazonaws.com/123456789012/" + replayTopic;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Failed to initialize ReplaySubscriptionMessageHandler: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Polls the SQS queue for replay messages at a fixed interval.
     * The messages come from the consolidated SNS topic but are delivered to a single SQS queue.
     */
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        if (replayQueueUrl == null) {
            LOGGER.warning("SQS queue URL is not initialized. Skipping message polling.");
            return;
        }
        
        // First, poll for messages outside the request context
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount")
            .withMessageAttributeNames("All"); // Request all message attributes
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        // Process each message in its own request context
        for (Message message : result.getMessages()) {
            processMessage(message);
        }
    }
    
    /**
     * Process a single SQS message within its own request context.
     * 
     * @param message The SQS message to process
     */
    private void processMessage(Message message) {
        try {
            // Extract the actual message from the SNS wrapper
            String messageBody = message.getBody();
            String unwrappedMessageBody = getUnwrappedMessageBody(messageBody);

            // Parse the message to extract headers before creating the request context
            ReplayMessage replayMessage = objectMapper.readValue(unwrappedMessageBody, ReplayMessage.class);
            
            // Extract headers from the message
            Map<String, String> headers = getHeaders(message, replayMessage);

            // Extract operation type from message attributes if available
            String operation = getOperation(message);
            LOGGER.info(() -> String.format("Processing %s message from queue: %s", operation, replayMessage.getBody().getReplayId()));

            requestScopeUtil.executeInRequestScope(() -> {
                try {
                    replayMessageHandler.handle(replayMessage);
                    sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, String.format("Error processing replay message: %s",e.getMessage()), e);
                    handleMessageError(message, unwrappedMessageBody);
                }
            }, headers);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error preparing replay message: %s", e.getMessage()), e);
            // If we can't even parse the message, just delete it
            sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
        }
    }

    private static Map<String, String> getHeaders(Message message, ReplayMessage replayMessage) {
        Map<String, String> headers = new HashMap<>();

        // First check for headers in the message attributes (SNS message attributes)
        if (message.getMessageAttributes() != null && !message.getMessageAttributes().isEmpty()) {
            for (Map.Entry<String, com.amazonaws.services.sqs.model.MessageAttributeValue> entry :
                 message.getMessageAttributes().entrySet()) {
                if (entry.getValue() != null && entry.getValue().getStringValue() != null) {
                    headers.put(entry.getKey(), entry.getValue().getStringValue());
                }
            }
        }

        // Then check for headers in the message body (for backward compatibility)
        if (replayMessage.getHeaders() != null) {
            headers.putAll(replayMessage.getHeaders());
        }

        headers.computeIfAbsent(DpsHeaders.CORRELATION_ID, k -> UUID.randomUUID().toString());

        return headers;
    }

    private static String getOperation(Message message) {
        String operation;
        if (message.getMessageAttributes() != null && message.getMessageAttributes().containsKey("operation")) {
            operation = message.getMessageAttributes().get("operation").getStringValue();
        } else {
            operation = "unknown";
        }
        return operation;
    }

    private String getUnwrappedMessageBody(String messageBody) {
        String unwrappedMessageBody = messageBody;

        // When a message comes from SNS to SQS, it's wrapped in an SNS envelope
        // We need to extract the actual message from the "Message" field
        try {
            JsonNode node = objectMapper.readTree(messageBody);
            if (node.has("Message")) {
                unwrappedMessageBody = node.get("Message").asText();
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error parsing SNS message wrapper: %s", e.getMessage()), e);
        }
        return unwrappedMessageBody;
    }

    /**
     * Handle errors that occur during message processing.
     * 
     * @param message The original SQS message
     * @param messageBody The unwrapped message body
     */
    private void handleMessageError(Message message, String messageBody) {
        try {
            ReplayMessage replayMessage = objectMapper.readValue(messageBody, ReplayMessage.class);
            int receiveCount = Integer.parseInt(message.getAttributes().get("ApproximateReceiveCount"));

            if (receiveCount >= MAX_DELIVERY_COUNT) {
                // Dead letter the message after max retries
                LOGGER.log(Level.SEVERE, () -> String.format("Max delivery attempts reached for message, sending to dead letter: %s", replayMessage.getBody().getReplayId()));
                replayMessageHandler.handleFailure(replayMessage);
                sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
            } else {
                // Return to queue for retry with backoff
                int visibilityTimeout = 30 * (int)Math.pow(2, (double)receiveCount - 1); // Exponential backoff
                LOGGER.info(() -> String.format("Returning message to queue for retry: %s with visibility timeout: %s", replayMessage.getBody().getReplayId(), visibilityTimeout));
                sqsClient.changeMessageVisibility(replayQueueUrl, message.getReceiptHandle(), visibilityTimeout);
            }
        } catch (Exception ex) {
            // If we can't even parse the message, just delete it
            LOGGER.log(Level.SEVERE, String.format("Failed to process message error handling, deleting from queue: %s", ex.getMessage()), ex);
            sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
        }
    }
}
