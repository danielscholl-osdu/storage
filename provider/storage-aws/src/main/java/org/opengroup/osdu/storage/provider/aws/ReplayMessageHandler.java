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
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
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
 * This class is responsible for sending and processing replay messages using SNS.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    
    private final AmazonSNS snsClient;
    
    private final ObjectMapper objectMapper;
    
    private final ReplayService replayService;
    
    private final JaxRsDpsLog logger;
    
    @Value("${aws.sns.replay-topic-arn}")
    private String replayTopicArn;
    
    @Value("${aws.sns.reindex-topic-arn}")
    private String reindexTopicArn;
    
    @Value("${aws.sns.records-topic-arn}")
    private String recordsTopicArn;
    
    @Value("${aws.region}")
    private String region;
    
    @Autowired
    public ReplayMessageHandler(ObjectMapper objectMapper, ReplayService replayService, JaxRsDpsLog logger, AmazonSNS snsClient) {
        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.replayService = replayService;
        this.logger = logger;
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
     * Sends replay messages to the appropriate SNS topic based on the operation.
     *
     * @param messages The replay messages to send
     * @param operation The operation type (e.g., "replay", "reindex")
     */
    public void sendReplayMessage(List<ReplayMessage> messages, String operation) {
        try {
            // Select appropriate topic based on operation
            String topicArn = getTopicArnForOperation(operation);
            
            for (ReplayMessage message : messages) {
                String messageBody = objectMapper.writeValueAsString(message);
                PublishRequest publishRequest = new PublishRequest()
                    .withTopicArn(topicArn)
                    .withMessage(messageBody);
                
                snsClient.publish(publishRequest);
                logger.info("Published replay message to SNS topic: " + topicArn + " for replayId: " + message.getBody().getReplayId());
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize replay message: " + e.getMessage(), e);
            throw new RuntimeException("Failed to serialize replay message", e);
        }
    }
    
    /**
     * Gets the appropriate SNS topic ARN based on the operation type.
     *
     * @param operation The operation type
     * @return The SNS topic ARN
     */
    private String getTopicArnForOperation(String operation) {
        if ("reindex".equals(operation)) {
            return reindexTopicArn;
        } else if ("replay".equals(operation)) {
            return recordsTopicArn;
        } else {
            return replayTopicArn;
        }
    }
}
