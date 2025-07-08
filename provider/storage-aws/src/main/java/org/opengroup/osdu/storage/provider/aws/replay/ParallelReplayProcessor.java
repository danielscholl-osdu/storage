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

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.ReplayMetadataItem;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles parallel processing of replay operations.
 * This component processes replay requests asynchronously using a thread pool,
 * batches kinds for efficient processing, and updates status in the repository.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ParallelReplayProcessor {
    private static final Logger LOGGER = Logger.getLogger(ParallelReplayProcessor.class.getName());
    private static final int RECORD_COUNT_BATCH_SIZE = 10;

    private final ExecutorService executorService;
    private final ReplayBatchConfig batchConfig;
    private final ReplayRepositoryImpl replayRepository;
    private final ReplayMessageHandler messageHandler;
    private final DpsHeaders headers;
    private final QueryRepositoryImpl queryRepository;
    private final RequestScopeUtil requestScopeUtil;

    /**
     * Creates a new ParallelReplayProcessor with the specified dependencies.
     *
     * @param replayExecutorService Thread pool for executing replay operations
     * @param batchConfig Configuration for batch processing
     * @param replayRepository Repository for storing replay metadata
     * @param messageHandler Handler for sending replay messages
     * @param headers HTTP headers for the current request
     * @param queryRepository Repository for querying record counts
     * @param requestScopeUtil Utility for executing code within a request scope
     */
    @Autowired
    public ParallelReplayProcessor(
            @Autowired(required = false) ExecutorService replayExecutorService,
            ReplayBatchConfig batchConfig,
            ReplayRepositoryImpl replayRepository,
            ReplayMessageHandler messageHandler,
            DpsHeaders headers,
            QueryRepositoryImpl queryRepository,
            RequestScopeUtil requestScopeUtil) {
        this.executorService = replayExecutorService;
        this.batchConfig = batchConfig;
        this.replayRepository = replayRepository;
        this.messageHandler = messageHandler;
        this.headers = headers;
        this.queryRepository = queryRepository;
        this.requestScopeUtil = requestScopeUtil;
    }

    /**
     * Processes a replay request asynchronously.
     * 
     * @param replayRequest The replay request to process
     * @param kinds The list of kinds to replay
     */
    public void processReplayAsync(ReplayRequest replayRequest, List<String> kinds) {
        String replayId = replayRequest.getReplayId();
        LOGGER.info(() -> String.format("Starting asynchronous replay for ID: %s with %d kinds", replayId, kinds.size()));
        
        // Capture the current request headers for use in the background thread
        final Map<String, String> requestHeaders = new HashMap<>(headers.getHeaders());

        if (executorService == null) {
            LOGGER.severe("ExecutorService is null. Cannot process replay request.");
            return;
        }

        executorService.submit(() -> processReplayInBackground(replayRequest, kinds, requestHeaders));
    }
    
    /**
     * Processes the replay in a background thread within the request scope.
     * 
     * @param replayRequest The replay request to process
     * @param kinds The list of kinds to replay
     * @param requestHeaders The headers from the original request
     */
    private void processReplayInBackground(ReplayRequest replayRequest, List<String> kinds, Map<String, String> requestHeaders) {
        // Execute within the request scope using the captured headers
        requestScopeUtil.executeInRequestScope(() -> {
            String replayId = replayRequest.getReplayId();
            try {
                // Update record counts for each kind
                updateRecordCounts(replayId, kinds);
                
                // Process kinds in batches
                List<List<String>> batches = createBatches(kinds, batchConfig.getBatchSize());
                LOGGER.info(() -> String.format("Created %d batches for replay ID: %s", batches.size(), replayId));

                processBatches(replayRequest, batches);

                LOGGER.info(() -> String.format("Completed sending all replay messages for ID: %s", replayId));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Error in asynchronous replay processing for ID %s: %s", 
                        replayId, e.getMessage()), e);
            }
        }, requestHeaders);
    }
    
    /**
     * Processes batches of kinds by updating their status and sending replay messages.
     * 
     * @param replayRequest The replay request
     * @param batches The batches of kinds to process
     */
    private void processBatches(ReplayRequest replayRequest, List<List<String>> batches) {
        for (List<String> batch : batches) {
            // Update status to QUEUED for all kinds in this batch
            updateBatchStatus(batch, replayRequest.getReplayId(), ReplayState.QUEUED);

            // Create and send messages for this batch - must run after replay status table update,
            // since messages can be fully processed before table update resulting in incorrect status of QUEUED.
            List<ReplayMessage> messages = createReplayMessages(replayRequest, batch);
            try {
                messageHandler.sendReplayMessage(messages, replayRequest.getOperation());
            } catch (ReplayMessageHandlerException e) {
                LOGGER.log(Level.SEVERE, "Failed to send replay messages for batch", e);
                // Update status to FAILED for all kinds in this batch
                updateBatchStatus(batch, replayRequest.getReplayId(), ReplayState.FAILED);
            }
        }
    }
    
    /**
     * Updates the status of all kinds in a batch to the specified state.
     * Uses batch save to reduce the number of DynamoDB API calls.
     * 
     * @param batch The batch of kinds to update
     * @param replayId The replay ID
     * @param state The ReplayState to set
     */
    private void updateBatchStatus(List<String> batch, String replayId, ReplayState state) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        
        LOGGER.info(() -> String.format("Updating status to %s for %d kinds in batch for replay ID: %s", 
                state.name(), batch.size(), replayId));
        
        try {
            List<AwsReplayMetaDataDTO> metadataList = retrieveMetadataWithState(batch, replayId, state);

            if (!metadataList.isEmpty()) {
                List<ReplayMetadataItem> failedBatches = replayRepository.batchSaveAwsReplayMetaData(metadataList);
                
                if (!failedBatches.isEmpty()) {
                    LOGGER.log(Level.SEVERE, () -> String.format("Failed to update status for %d batches", failedBatches.size()));
                    
                    // Log details of failed items
                    for (ReplayMetadataItem failedBatch : failedBatches) {
                        LOGGER.log(Level.SEVERE, () -> String.format("Batch failure: %s.",
                                failedBatch.getId()));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error during batch update of status to %s: %s", state.name(), e.getMessage()), e);
        }
    }

    private List<AwsReplayMetaDataDTO> retrieveMetadataWithState(List<String> batch, String replayId, ReplayState state) {
        List<AwsReplayMetaDataDTO> metadataList = new ArrayList<>();

        try {
            // Use batch load to efficiently retrieve all metadata in a single API call
            List<AwsReplayMetaDataDTO> batchResults = replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
            
            // Update state and lastUpdatedAt for all retrieved items
            for (AwsReplayMetaDataDTO replayMetaData : batchResults) {
                replayMetaData.setState(state.name());
                replayMetaData.setLastUpdatedAt(new Date());
                metadataList.add(replayMetaData);
            }
            
            // Log any kinds that weren't found in the batch retrieval
            if (batchResults.size() < batch.size()) {
                Set<String> retrievedKinds = batchResults.stream()
                        .map(AwsReplayMetaDataDTO::getKind)
                        .collect(Collectors.toSet());
                
                List<String> missingKinds = batch.stream()
                        .filter(kind -> !retrievedKinds.contains(kind))
                        .toList();
                
                if (!missingKinds.isEmpty()) {
                    LOGGER.warning(() -> String.format("Could not find metadata for %d kinds: %s", 
                            missingKinds.size(), String.join(", ", missingKinds)));
                }
            }
            
            // Note: We don't save here anymore - the caller will handle the batch save
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error batch retrieving metadata for replay ID %s: %s", 
                    replayId, e.getMessage()), e);
            
            // Fall back to individual retrievals if batch fails
            LOGGER.info("Falling back to individual retrievals");
            for (String kind : batch) {
                try {
                    AwsReplayMetaDataDTO replayMetaData = replayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId);
                    if (replayMetaData != null) {
                        replayMetaData.setState(state.name());
                        replayMetaData.setLastUpdatedAt(new Date());
                        metadataList.add(replayMetaData);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error retrieving metadata for kind %s: %s", kind, ex.getMessage()), ex);
                }
            }
        }
        
        return metadataList;
    }

    /**
     * Updates record counts for each kind.
     * 
     * @param replayId The replay ID
     * @param kinds The list of kinds to update
     */
    private void updateRecordCounts(String replayId, List<String> kinds) {
        LOGGER.info(() -> String.format("Updating record counts for %d kinds for replay ID: %s", kinds.size(), replayId));
        
        // Process kinds in smaller batches to avoid overloading the database
        List<List<String>> batches = createBatches(kinds, RECORD_COUNT_BATCH_SIZE);
        
        for (List<String> batch : batches) {
            updateRecordCountsForBatch(batch, replayId);
        }
    }
    
    /**
     * Updates record counts for a batch of kinds.
     * Uses batch save to reduce the number of DynamoDB API calls.
     * 
     * @param batch The batch of kinds to update
     * @param replayId The replay ID
     */
    private void updateRecordCountsForBatch(List<String> batch, String replayId) {
        try {
            // Get counts for this batch of kinds
            Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(batch);
            
            // Prepare a list of metadata records to update
            List<AwsReplayMetaDataDTO> metadataToUpdate = retrieveMetadataRecordsWithCounts(batch, replayId, kindCounts);

            // Save all updated records in a single batch operation
            if (!metadataToUpdate.isEmpty()) {
                List<ReplayMetadataItem> failedBatches = replayRepository.batchSaveAwsReplayMetaData(metadataToUpdate);

                if (!failedBatches.isEmpty()) {
                    LOGGER.log(Level.SEVERE, ()-> String.format("Failed to update record counts for %d batches", failedBatches.size()));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error getting record counts for batch: %s", e.getMessage()), e);
        }
    }

    private List<AwsReplayMetaDataDTO> retrieveMetadataRecordsWithCounts(List<String> batch, String replayId, Map<String, Long> kindCounts) {
        List<AwsReplayMetaDataDTO> metadataToUpdate = new ArrayList<>();
        
        try {
            // Use batch load to efficiently retrieve all metadata in a single API call
            List<AwsReplayMetaDataDTO> batchResults = replayRepository.batchGetAwsReplayStatusByKindsAndReplayId(batch, replayId);
            
            // Update record counts for all retrieved items
            for (AwsReplayMetaDataDTO replayMetaData : batchResults) {
                replayMetaData.setTotalRecords(kindCounts.getOrDefault(replayMetaData.getKind(), 0L));
                replayMetaData.setLastUpdatedAt(new Date());
                metadataToUpdate.add(replayMetaData);
            }
            
            // Log any kinds that weren't found in the batch retrieval
            if (batchResults.size() < batch.size()) {
                Set<String> retrievedKinds = batchResults.stream()
                        .map(AwsReplayMetaDataDTO::getKind)
                        .collect(Collectors.toSet());
                
                List<String> missingKinds = batch.stream()
                        .filter(kind -> !retrievedKinds.contains(kind))
                        .toList();
                
                if (!missingKinds.isEmpty()) {
                    LOGGER.warning(() -> String.format("Could not find metadata for %d kinds when updating counts: %s", 
                            missingKinds.size(), String.join(", ", missingKinds)));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error batch retrieving metadata for record counts: %s", e.getMessage()), e);
            
            // Fall back to individual retrievals if batch fails
            LOGGER.info("Falling back to individual retrievals for record counts");
            for (String kind : batch) {
                try {
                    AwsReplayMetaDataDTO replayMetaData = replayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId);
                    if (replayMetaData != null) {
                        replayMetaData.setTotalRecords(kindCounts.getOrDefault(kind, 0L));
                        replayMetaData.setLastUpdatedAt(new Date());
                        metadataToUpdate.add(replayMetaData);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error retrieving metadata for kind %s: %s", kind, ex.getMessage()), ex);
                }
            }
        }
        
        return metadataToUpdate;
    }

    /**
     * Creates batches from a list of kinds.
     * 
     * @param kinds The list of kinds to batch
     * @param batchSize The size of each batch
     * @return A list of batches
     */
    private List<List<String>> createBatches(List<String> kinds, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i += batchSize) {
            batches.add(kinds.subList(i, Math.min(kinds.size(), i + batchSize)));
        }
        return batches;
    }



    /**
     * Creates replay messages for a batch of kinds.
     *
     * @param replayRequest The replay request
     * @param kinds The batch of kinds
     * @return A list of replay messages
     */
    private List<ReplayMessage> createReplayMessages(ReplayRequest replayRequest, List<String> kinds) {
        List<ReplayMessage> messages = new ArrayList<>();
        String replayId = replayRequest.getReplayId();
        String operation = replayRequest.getOperation();
        int kindCounter = 0;

        for (String kind : kinds) {
            ReplayMessage message = createReplayMessage(kind, replayId, operation, kindCounter);
            messages.add(message);
            kindCounter++;
        }
        
        return messages;
    }
    
    /**
     * Creates a replay message for a single kind.
     * 
     * @param kind The kind to replay
     * @param replayId The replay ID
     * @param operation The operation type
     * @param kindCounter The counter for correlation ID generation
     * @return A replay message
     */
    private ReplayMessage createReplayMessage(String kind, String replayId, String operation, int kindCounter) {
        // Create the replay data
        ReplayData body = new ReplayData();
        body.setId(UUID.randomUUID().toString());
        body.setReplayId(replayId);
        body.setKind(kind);
        body.setOperation(operation);
        body.setReplayType(ReplayType.REPLAY_KIND.name());
        body.setStartAtTimestamp(System.currentTimeMillis());

        // Create the message
        ReplayMessage message = new ReplayMessage();
        message.setBody(body);
        
        // Add headers from the current request
        String messageCorrelationId = ReplayUtils.getNextCorrelationId(headers.getCorrelationId(), Optional.of(kindCounter));
        Map<String, String> messageHeaders = ReplayUtils.createHeaders(headers.getPartitionId(), messageCorrelationId);
        message.setHeaders(messageHeaders);
        
        return message;
    }

    /**
     * Cleanup resources when the bean is destroyed.
     */
    @PreDestroy
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            LOGGER.info("Shutting down replay executor service");
            executorService.shutdown();
        }
    }
}
