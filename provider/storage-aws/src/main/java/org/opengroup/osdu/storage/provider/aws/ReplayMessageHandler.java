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
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handler for replay messages in AWS.
 * This class is responsible for sending and processing replay messages using SQS.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    
    private final AmazonSQS sqsClient;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private ReplayService replayService;
    
    @Autowired
    private JaxRsDpsLog logger;
    
    @Value("${aws.sqs.replay-queue-url}")
    private String replayQueueUrl;
    
    @Value("${aws.sqs.reindex-queue-url}")
    private String reindexQueueUrl;
    
    @Value("${aws.sqs.records-queue-url}")
    private String recordsQueueUrl;
    
    @Value("${aws.region}")
    private String region;
    
    public ReplayMessageHandler() {
        // Initialize SQS client
        this.sqsClient = AmazonSQSClientBuilder.standard()
            .withRegion(System.getProperty("aws.region", "us-east-1"))
            .build();
    }
    
    /**
     * Handles a replay message by processing it through the replay service.
     *
     * @param message The replay message to handle
     */
    public void handle(ReplayMessage message) {
        try {
            logger.info("Processing replay message: " + message.getBody().getReplayId() + " for kind: " + message.getBody().getKind());
            // Process the replay message
            replayService.processReplayMessage(message);
        } catch (Exception e) {
            logger.error("Error processing replay message: " + e.getMessage(), e);
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
        logger.error("Processing failure for replay message: " + message.getBody().getReplayId() + " for kind: " + message.getBody().getKind());
        replayService.processFailure(message);
    }
    
    /**
     * Sends replay messages to the appropriate SQS queue based on the operation.
     *
     * @param messages The replay messages to send
     * @param operation The operation type (e.g., "replay", "reindex")
     */
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        try {
            // Select appropriate queue based on operation
            String queueUrl = getQueueUrlForOperation(operation);
            
            for (ReplayMessage message : messages) {
                String messageBody = objectMapper.writeValueAsString(message);
                SendMessageRequest sendMessageRequest = new SendMessageRequest()
                    .withQueueUrl(queueUrl)
                    .withMessageBody(messageBody);
                
                sqsClient.sendMessage(sendMessageRequest);
                logger.info("Sent replay message to queue: " + queueUrl + " for replayId: " + message.getBody().getReplayId());
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize replay message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to serialize replay message", e);
        }
    }
    
    /**
     * Gets the appropriate SQS queue URL based on the operation type.
     *
     * @param operation The operation type
     * @return The SQS queue URL
     */
    private String getQueueUrlForOperation(String operation) {
        switch (operation) {
            case "reindex":
                return reindexQueueUrl;
            case "replay":
                return recordsQueueUrl;
            default:
                return replayQueueUrl;
        }
    }
}
