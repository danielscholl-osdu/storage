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

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.v2.sqs.AmazonSQSConfig;
import org.opengroup.osdu.core.aws.v2.ssm.K8sLocalParameterProvider;
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
    private static final Logger logger = Logger.getLogger(ReplaySubscriptionMessageHandler.class.getName());
    
    private SqsClient sqsClient;
    
    private final ReplayMessageHandler replayMessageHandler;
    
    private final ObjectMapper objectMapper;
    
    private final RequestScopeUtil requestScopeUtil;

    @Value("${AWS.REGION:us-east-1}")
    private String region;
    
    @Value("${REPLAY_TOPIC:replay-records}")
    private String replayTopic;
    
    @Value("${replay.visibility-timeout-seconds:300}")
    private int visibilityTimeoutSeconds;
    
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

            K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
            replayQueueUrl = provider.getParameterAsStringOrDefault(replayTopic + "-sqs-queue-url", "dummy-topic-to-prevent-runtime-failure");
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Failed to initialize ReplaySubscriptionMessageHandler: %s", e.getMessage()), e);
        }
    }

    /**
     * Polls the SQS queue for replay messages at a fixed interval.
     * The messages come from the consolidated SNS topic but are delivered to a single SQS queue.
     */
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        if (replayQueueUrl == null) {
            logger.warning("SQS queue URL is not initialized. Skipping message polling.");
            return;
        }
        
        // First, poll for messages outside the request context
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(replayQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .visibilityTimeout(visibilityTimeoutSeconds)
                .messageSystemAttributeNamesWithStrings("ApproximateReceiveCount")
                .messageAttributeNames("All")
                .build(); // Request all message attributes
            
        ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
        
        // Process each message in its own request context
        for (Message message : response.messages()) {
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
            String messageBody = message.body();
            String unwrappedMessageBody = getUnwrappedMessageBody(messageBody);

            // Parse the message to extract headers before creating the request context
            ReplayMessage replayMessage = objectMapper.readValue(unwrappedMessageBody, ReplayMessage.class);
            
            // Extract headers from the message
            Map<String, String> headers = getHeaders(message, replayMessage);

            // Extract operation type from message attributes if available
            String operation = getOperation(message);
            logger.info(() -> String.format("Processing %s message from queue: %s", operation, replayMessage.getBody().getReplayId()));

            requestScopeUtil.executeInRequestScope(() -> {
                try {
                    replayMessageHandler.handle(replayMessage);
                    sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(replayQueueUrl).receiptHandle(message.receiptHandle()).build());
                } catch (Exception e) {
                    logger.log(Level.SEVERE, String.format("Error processing replay message: %s",e.getMessage()), e);
                    handleMessageError(message, unwrappedMessageBody);
                }
            }, headers);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Error preparing replay message: %s", e.getMessage()), e);
            // If we can't even parse the message, just delete it
            sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(replayQueueUrl).receiptHandle(message.receiptHandle()).build());
        }
    }

    private static Map<String, String> getHeaders(Message message, ReplayMessage replayMessage) {
        Map<String, String> headers = new HashMap<>();

        // First check for headers in the message attributes (SNS message attributes)
        if (message.messageAttributes() != null && !message.messageAttributes().isEmpty()) {
            for (Map.Entry<String, MessageAttributeValue> entry :
                 message.messageAttributes().entrySet()) {
                if (entry.getValue() != null && entry.getValue().stringValue() != null) {
                    headers.put(entry.getKey(), entry.getValue().stringValue());
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
        if (message.messageAttributes() != null && message.messageAttributes().containsKey("operation")) {
            operation = message.messageAttributes().get("operation").stringValue();
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
            logger.log(Level.SEVERE, String.format("Error parsing SNS message wrapper: %s", e.getMessage()), e);
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
            int receiveCount = Integer.parseInt(message.attributesAsStrings().get("ApproximateReceiveCount"));

            if (receiveCount >= MAX_DELIVERY_COUNT) {
                // Dead letter the message after max retries
                logger.log(Level.SEVERE, () -> String.format("Max delivery attempts reached for message, sending to dead letter: %s", replayMessage.getBody().getReplayId()));
                replayMessageHandler.handleFailure(replayMessage);
                sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(replayQueueUrl).receiptHandle(message.receiptHandle()).build());
            } else {
                // Return to queue for retry with backoff
                int backoffMultiplier = (int)Math.pow(2, (double)receiveCount - 1);
                int retryVisibilityTimeout = Math.max(visibilityTimeoutSeconds, 30 * backoffMultiplier); // Use configured timeout or backoff, whichever is greater
                logger.info(() -> String.format("Returning message to queue for retry: %s with visibility timeout: %s", replayMessage.getBody().getReplayId(), retryVisibilityTimeout));
                sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder().queueUrl(replayQueueUrl).receiptHandle(message.receiptHandle()).visibilityTimeout(retryVisibilityTimeout).build());
            }
        } catch (Exception ex) {
            // If we can't even parse the message, just delete it
            logger.log(Level.SEVERE, String.format("Failed to process message error handling, deleting from queue: %s", ex.getMessage()), ex);
            sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(replayQueueUrl).receiptHandle(message.receiptHandle()).build());
        }
    }
}
