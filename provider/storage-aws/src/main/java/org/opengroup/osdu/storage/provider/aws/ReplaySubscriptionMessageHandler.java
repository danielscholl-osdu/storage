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
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.sqs.AmazonSQSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * SQS message listener for replay messages.
 * This class polls the SQS queue for replay messages and processes them.
 * The messages are published to SNS topics but consumed from SQS queues.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    
    private AmazonSQS sqsClient;
    
    @Inject
    private ReplayMessageHandler replayMessageHandler;
    
    @Inject
    private ObjectMapper objectMapper;
    
    @Inject
    private JaxRsDpsLog logger;
    
    private final int MAX_DELIVERY_COUNT = 3;
    
    @Value("${AWS.REGION}")
    private String region;
    
    @Value("${REPLAY_TOPIC}")
    private String replayTopic;
    
    private String replayQueueUrl;
    
    @PostConstruct
    public void init() throws K8sParameterNotFoundException {
        // Initialize SQS client
        AmazonSQSConfig sqsConfig = new AmazonSQSConfig(region);
        this.sqsClient = sqsConfig.AmazonSQS();
        
        // Get queue URL from SSM parameters
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        try {
            replayQueueUrl = provider.getParameterAsString(replayTopic + "-sqs-queue-url");
        } catch (K8sParameterNotFoundException e) {
            logger.error("Failed to retrieve SQS queue URL from SSM: " + e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Polls the SQS queue for replay messages at a fixed interval.
     * The messages come from SNS topics but are delivered to SQS queues.
     */
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        if (replayQueueUrl == null) {
            logger.error("SQS queue URL is not initialized. Skipping message polling.");
            return;
        }
        
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount");
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        for (Message message : result.getMessages()) {
            try {
                // Extract the actual message from the SNS wrapper
                String messageBody = message.getBody();
                
                // When a message comes from SNS to SQS, it's wrapped in an SNS envelope
                // We need to extract the actual message from the "Message" field
                try {
                    JsonNode node = objectMapper.readTree(messageBody);
                    if (node.has("Message")) {
                        messageBody = node.get("Message").asText();
                    }
                } catch (Exception e) {
                    logger.error("Error parsing SNS message wrapper: " + e.getMessage(), e);
                }
                
                ReplayMessage replayMessage = objectMapper.readValue(messageBody, ReplayMessage.class);
                logger.info("Processing replay message from queue: " + replayMessage.getBody().getReplayId());
                replayMessageHandler.handle(replayMessage);
                sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
            } catch (Exception e) {
                logger.error("Error processing replay message: " + e.getMessage(), e);
                try {
                    // Extract the message for error handling
                    String messageBody = message.getBody();
                    try {
                        JsonNode node = objectMapper.readTree(messageBody);
                        if (node.has("Message")) {
                            messageBody = node.get("Message").asText();
                        }
                    } catch (Exception ex) {
                        logger.error("Error parsing SNS message wrapper during error handling: " + ex.getMessage(), ex);
                    }
                    
                    ReplayMessage replayMessage = objectMapper.readValue(messageBody, ReplayMessage.class);
                    int receiveCount = Integer.parseInt(message.getAttributes().get("ApproximateReceiveCount"));
                    if (receiveCount >= MAX_DELIVERY_COUNT) {
                        // Dead letter the message after max retries
                        logger.error("Max delivery attempts reached for message, sending to dead letter: " + replayMessage.getBody().getReplayId());
                        replayMessageHandler.handleFailure(replayMessage);
                        sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
                    } else {
                        // Return to queue for retry with backoff
                        int visibilityTimeout = 30 * (int)Math.pow(2, receiveCount - 1); // Exponential backoff
                        logger.info("Returning message to queue for retry: " + replayMessage.getBody().getReplayId() + " with visibility timeout: " + visibilityTimeout);
                        sqsClient.changeMessageVisibility(replayQueueUrl, message.getReceiptHandle(), visibilityTimeout);
                    }
                } catch (Exception ex) {
                    // If we can't even parse the message, just delete it
                    logger.error("Failed to process message, deleting from queue: " + ex.getMessage(), ex);
                    sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
                }
            }
        }
    }
}
