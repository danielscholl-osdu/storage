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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handler for replay messages in AWS.
 * This class is responsible for sending and processing replay messages using SNS.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageHandler {
    // Use a standard Java logger for all logging
    private static final Logger LOGGER = Logger.getLogger(ReplayMessageHandler.class.getName());
    
    private AmazonSNS snsClient;
    
    @Inject
    private ObjectMapper objectMapper;
    
    @Inject
    private ReplayService replayService;
    
    @Value("${AWS.REGION:us-east-1}")
    private String region;
    
    @Value("${REPLAY_TOPIC:replay-records}")
    private String replayTopic;
    
    @Value("${REINDEX_TOPIC:reindex-records}")
    private String reindexTopic;

    private String replayTopicArn;
    private String reindexTopicArn;
    
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
                reindexTopicArn = provider.getParameterAsString(reindexTopic + "-sns-topic-arn");
                LOGGER.info("Retrieved SNS topic ARNs from SSM parameters");
            } catch (K8sParameterNotFoundException e) {
                // Fallback to default ARNs for development
                LOGGER.warning("Failed to retrieve SNS topic ARNs from SSM, using default values: " + e.getMessage());
                replayTopicArn = "arn:aws:sns:" + region + ":123456789012:" + replayTopic;
                reindexTopicArn = "arn:aws:sns:" + region + ":123456789012:" + reindexTopic;
            }
            
            LOGGER.info("ReplayMessageHandler initialized with region: " + region);
        } catch (Exception e) {
            // Use standard Java logger for errors during initialization
            LOGGER.log(Level.SEVERE, "Failed to initialize ReplayMessageHandler: " + e.getMessage(), e);
        }
    }
    
    @Inject
    private ReplayMessageProcessorAWSImpl replayMessageProcessor;
    
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
                LOGGER.info("Published replay message to SNS topic: " + topicArn + " for replayId: " + message.getBody().getReplayId());
            }
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize replay message: " + e.getMessage(), e);
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
        }
        return replayTopicArn;
    }
}
