package org.opengroup.osdu.storage.provider.aws;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.provider.aws.config.ReplayBatchConfig;
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
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ParallelReplayProcessor {
    private static final Logger LOGGER = Logger.getLogger(ParallelReplayProcessor.class.getName());

    private final ExecutorService executorService;
    private final ReplayBatchConfig batchConfig;
    private final IReplayRepository replayRepository;
    private final ReplayMessageHandler messageHandler;
    private final DpsHeaders headers;
    private final QueryRepositoryImpl queryRepository;
    private final RequestScopeUtil requestScopeUtil;

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
     */
    public void processReplayAsync(ReplayRequest replayRequest, List<String> kinds) {
        String replayId = replayRequest.getReplayId();
        LOGGER.info("Starting asynchronous replay for ID: " + replayId + " with " + kinds.size() + " kinds");
        
        // Capture the current request headers for use in the background thread
        final Map<String, String> requestHeaders = new HashMap<>(headers.getHeaders());

        executorService.submit(() -> {
            // Execute within the request scope using the captured headers
            requestScopeUtil.executeInRequestScope(() -> {
                try {
                    // Update record counts for each kind
                    updateRecordCounts(replayId, kinds);
                    
                    // Process kinds in batches
                    List<List<String>> batches = createBatches(kinds, batchConfig.getBatchSize());
                    LOGGER.info("Created " + batches.size() + " batches for replay ID: " + replayId);

                    for (List<String> batch : batches) {
                        // Create and send messages for this batch
                        List<ReplayMessage> messages = createReplayMessages(replayRequest, batch);
                        messageHandler.sendReplayMessage(messages, replayRequest.getOperation());
                        
                        // Update status to QUEUED for all kinds in this batch
                        for (String kind : batch) {
                            try {
                                ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
                                if (replayMetaData != null) {
                                    replayMetaData.setState(ReplayState.QUEUED.name());
                                    replayRepository.save(replayMetaData);
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.SEVERE, "Error updating status for kind " + kind + ": " + e.getMessage(), e);
                            }
                        }
                    }

                    LOGGER.info("Completed sending all replay messages for ID: " + replayId);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error in asynchronous replay processing: " + e.getMessage(), e);
                }
            }, requestHeaders);
        });
    }
    
    /**
     * Updates record counts for each kind.
     */
    private void updateRecordCounts(String replayId, List<String> kinds) {
        LOGGER.info("Updating record counts for " + kinds.size() + " kinds for replay ID: " + replayId);
        
        // Process kinds in smaller batches to avoid overloading the database
        List<List<String>> batches = createBatches(kinds, 10);
        
        for (List<String> batch : batches) {
            try {
                // Get counts for this batch of kinds
                Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(batch);
                
                // Update metadata records with the counts
                for (String kind : batch) {
                    try {
                        ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
                        if (replayMetaData != null) {
                            Long count = kindCounts.getOrDefault(kind, 0L);
                            replayMetaData.setTotalRecords(count);
                            replayRepository.save(replayMetaData);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error updating record count for kind " + kind + ": " + e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error getting record counts for batch: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates batches from a list of kinds.
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
     */
    private List<ReplayMessage> createReplayMessages(ReplayRequest replayRequest, List<String> kinds) {
        List<ReplayMessage> messages = new ArrayList<>();
        String replayId = replayRequest.getReplayId();
        String operation = replayRequest.getOperation();
        int kindCounter = 0;

        for (String kind : kinds) {
            // Create the replay data
            org.opengroup.osdu.storage.dto.ReplayData body = new org.opengroup.osdu.storage.dto.ReplayData();
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
            
            messages.add(message);
            kindCounter++;
        }
        
        return messages;
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
