package org.opengroup.osdu.storage.provider.aws;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
import org.opengroup.osdu.storage.provider.aws.exception.ReplayMessageHandlerException;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.*;
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
    private final IReplayRepository replayRepository;
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
            IReplayRepository replayRepository,
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
            updateBatchStatusToQueued(batch, replayRequest.getReplayId());

            // Create and send messages for this batch - must run after replay status table update,
            // since messages can be fully processed before table update resulting in incorrect status of QUEUED.
            List<ReplayMessage> messages = createReplayMessages(replayRequest, batch);
            try {
                messageHandler.sendReplayMessage(messages, replayRequest.getOperation());
            } catch (ReplayMessageHandlerException e) {
                LOGGER.log(Level.SEVERE, "Failed to send replay messages for batch", e);
                // Update status to FAILED for all kinds in this batch
                updateBatchStatusToFailed(batch, replayRequest.getReplayId(), e.getMessage());
            }
        }
    }
    
    /**
     * Updates the status of all kinds in a batch.
     * 
     * @param batch The batch of kinds to update
     * @param replayId The replay ID
     */
    private void updateBatchStatusToQueued(List<String> batch, String replayId) {
        for (String kind : batch) {
            try {
                ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
                if (replayMetaData != null) {
                    replayMetaData.setState(ReplayState.QUEUED.name());
                    replayRepository.save(replayMetaData);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Error updating status for kind %s: %s", kind, e.getMessage()), e);
            }
        }
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
     * 
     * @param batch The batch of kinds to update
     * @param replayId The replay ID
     */
    private void updateRecordCountsForBatch(List<String> batch, String replayId) {
        try {
            // Get counts for this batch of kinds
            Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(batch);
            
            // Update metadata records with the counts
            for (String kind : batch) {
                updateRecordCountForKind(kind, replayId, kindCounts.getOrDefault(kind, 0L));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error getting record counts for batch: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Updates the record count for a single kind.
     * 
     * @param kind The kind to update
     * @param replayId The replay ID
     * @param count The record count
     */
    private void updateRecordCountForKind(String kind, String replayId, Long count) {
        try {
            ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setTotalRecords(count);
                replayRepository.save(replayMetaData);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error updating record count for kind %s: %s", kind, e.getMessage()), e);
        }
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
     * Updates the status of all kinds in a batch to FAILED.
     * 
     * @param batch The batch of kinds to update
     * @param replayId The replay ID
     * @param errorMessage The error message
     */
    private void updateBatchStatusToFailed(List<String> batch, String replayId, String errorMessage) {
        for (String kind : batch) {
            try {
                ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
                if (replayMetaData != null) {
                    replayMetaData.setState(ReplayState.FAILED.name());
                    replayRepository.save(replayMetaData);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Error updating failure status for kind %s: %s", kind, e.getMessage()), e);
            }
        }
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
