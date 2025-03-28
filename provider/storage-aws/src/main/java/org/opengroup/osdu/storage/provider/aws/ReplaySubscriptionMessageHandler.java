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
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.service.replay.ReplayMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SQS message listener for replay messages.
 * This class polls the SQS queue for replay messages and processes them.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler {
    
    private final AmazonSQS sqsClient;
    private final ReplayMessageHandler replayMessageHandler;
    private final ObjectMapper objectMapper;
    private final JaxRsDpsLog logger;
    private final int MAX_DELIVERY_COUNT = 3;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    @Value("${aws.region}")
    private String region;
    
    @Autowired
    public ReplaySubscriptionMessageHandler(ReplayMessageHandler replayMessageHandler,
                                           ObjectMapper objectMapper,
                                           JaxRsDpsLog logger) {
        this.sqsClient = AmazonSQSClientBuilder.standard()
            .withRegion(System.getProperty("aws.region", "us-east-1"))
            .build();
        this.replayMessageHandler = replayMessageHandler;
        this.objectMapper = objectMapper;
        this.logger = logger;
    }
    
    /**
     * Polls the SQS queue for replay messages at a fixed interval.
     */
    @Scheduled(fixedDelayString = "${aws.sqs.polling-interval-ms:1000}")
    public void pollMessages() {
        ReceiveMessageRequest receiveRequest = new ReceiveMessageRequest()
            .withQueueUrl(replayQueueUrl)
            .withMaxNumberOfMessages(10)
            .withWaitTimeSeconds(5)
            .withAttributeNames("ApproximateReceiveCount");
            
        ReceiveMessageResult result = sqsClient.receiveMessage(receiveRequest);
        
        for (Message message : result.getMessages()) {
            try {
                ReplayMessage replayMessage = objectMapper.readValue(message.getBody(), ReplayMessage.class);
                logger.info("Processing replay message from queue: " + replayMessage.getReplayId());
                replayMessageHandler.handle(replayMessage);
                sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
            } catch (Exception e) {
                logger.error("Error processing replay message: " + e.getMessage(), e);
                try {
                    ReplayMessage replayMessage = objectMapper.readValue(message.getBody(), ReplayMessage.class);
                    int receiveCount = Integer.parseInt(message.getAttributes().get("ApproximateReceiveCount"));
                    if (receiveCount >= MAX_DELIVERY_COUNT) {
                        // Dead letter the message after max retries
                        logger.error("Max delivery attempts reached for message, sending to dead letter: " + replayMessage.getReplayId());
                        replayMessageHandler.handleFailure(replayMessage);
                        sqsClient.deleteMessage(replayQueueUrl, message.getReceiptHandle());
                    } else {
                        // Return to queue for retry with backoff
                        int visibilityTimeout = 30 * (int)Math.pow(2, receiveCount - 1); // Exponential backoff
                        logger.info("Returning message to queue for retry: " + replayMessage.getReplayId() + " with visibility timeout: " + visibilityTimeout);
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
