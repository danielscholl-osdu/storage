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

import lombok.Getter;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import java.util.Date;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AWS implementation for processing replay messages.
 * This class handles the actual processing of replay messages.
 */
@Component
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayMessageProcessorAWSImpl {
    
    private static final Logger LOGGER = Logger.getLogger(ReplayMessageProcessorAWSImpl.class.getName());
    
    @Value("${replay.message.default-batch-size:1000}")
    private int defaultBatchSize;
    
    @Value("${replay.message.publish-batch-size:50}")
    private int publishBatchSize;
    
    private static final String RECORD_BLOCKS = "data metadata";
    
    private final IReplayRepository replayRepository;
    private final QueryRepositoryImpl queryRepository;
    private final IMessageBus messageBus;
    private final DpsHeaders headers;
    private final StorageAuditLogger auditLogger;
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    private final WorkerThreadPool workerThreadPool;

    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    private String recordMetadataTableParameterRelativePath;

    @Autowired
    public ReplayMessageProcessorAWSImpl(IReplayRepository replayRepository, 
                                        QueryRepositoryImpl queryRepository, 
                                        IMessageBus messageBus, 
                                        DpsHeaders headers, 
                                        StorageAuditLogger auditLogger,
                                        DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory,
                                        WorkerThreadPool workerThreadPool) {
        this.replayRepository = replayRepository;
        this.queryRepository = queryRepository;
        this.messageBus = messageBus;
        this.headers = headers;
        this.auditLogger = auditLogger;
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.workerThreadPool = workerThreadPool;
    }

    /**
     * Processes a replay message.
     *
     * @param replayMessage The replay message to process
     */
    public void processReplayMessage(ReplayMessage replayMessage) {
        if (replayMessage == null || replayMessage.getBody() == null) {
            LOGGER.severe("Received null replay message or message body");
            return;
        }
        
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        try {
            LOGGER.info(() -> String.format("Processing replay message for kind: %s and replayId: %s", kind, replayId));
            
            // Get AWS-specific replay metadata to check for resume information
            ReplayRepositoryImpl awsReplayRepository = (ReplayRepositoryImpl) replayRepository;
            AwsReplayMetaDataDTO awsReplayMetaData = awsReplayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId);
            
            // Check if this kind is already completed for this replay ID
            if (awsReplayMetaData != null && ReplayState.COMPLETED.name().equals(awsReplayMetaData.getState())) {
                LOGGER.info(() -> String.format("Skipping already completed replay for kind: %s and replayId: %s", 
                                               kind, replayId));
                return;
            }
            
            // Update status to IN_PROGRESS
            if (awsReplayMetaData != null) {
                awsReplayMetaData.setState(ReplayState.IN_PROGRESS.name());
                awsReplayRepository.saveAwsReplayMetaData(awsReplayMetaData);
                
                // Check if we're resuming a previous attempt
                String lastCursor = awsReplayMetaData.getLastCursor();
                if (lastCursor != null && !lastCursor.isEmpty()) {
                    LOGGER.info(() -> String.format("Resuming replay from cursor: %s for kind: %s and replayId: %s", 
                                                   lastCursor, kind, replayId));
                }
                
                processRecordsInBatches(replayMessage, awsReplayMetaData);
                
                // Update status to COMPLETED
                updateReplayStatusToCompleted(kind, replayId, awsReplayMetaData.getProcessedRecords());
                
                LOGGER.info(() -> String.format("Completed processing replay message for kind: %s and replayId: %s", kind, replayId));
            } else {
                LOGGER.warning(() -> String.format("No replay metadata found for kind: %s and replayId: %s", kind, replayId));
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error processing replay message: %s", e.getMessage()), e);
            processFailure(replayMessage);
        }
    }
    
    /**
     * Process records in batches for the given replay message
     * 
     * @param replayMessage The replay message
     * @param replayMetaData The replay metadata for status updates
     */
    private void processRecordsInBatches(ReplayMessage replayMessage, AwsReplayMetaDataDTO replayMetaData) {
        String kind = replayMessage.getBody().getKind();
        String cursor = replayMetaData.getLastCursor(); // Start from the last cursor if available
        long processedRecords = replayMetaData.getProcessedRecords() != null ? replayMetaData.getProcessedRecords() : 0;
        
        // Get AWS-specific replay repository
        ReplayRepositoryImpl awsReplayRepository = (ReplayRepositoryImpl) replayRepository;
        
        logResumeInfo(cursor, processedRecords);
        
        boolean hasMoreRecords = true;
        
        while (hasMoreRecords) {
            try {
                // Fetch records for this batch
                RecordInfoQueryResult<RecordId> recordIds = fetchRecordBatch(kind, cursor);
                
                // Process batch and determine if we should continue
                BatchProcessingResult batchResult = processBatchAndUpdateCursor(replayMessage, recordIds, processedRecords);
                processedRecords = batchResult.processedRecords();
                cursor = batchResult.cursor();
                hasMoreRecords = batchResult.hasMoreRecords();
                
                // Update metadata with progress
                updateReplayMetadata(replayMetaData, processedRecords, cursor, awsReplayRepository);
                
                logProcessingProgress(processedRecords, kind);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Error processing batch for kind %s: %s", kind, e.getMessage()), e);
            }
        }
    }
    
    /**
     * Log information about resuming from a previous cursor
     */
    private void logResumeInfo(String cursor, long processedRecords) {
        if (cursor != null && !cursor.isEmpty()) {
            String finalCursor = cursor;
            long finalProcessedRecords = processedRecords;
            LOGGER.info(() -> String.format("Resuming processing from cursor: %s with %d records already processed",
                    finalCursor, finalProcessedRecords));
        }
    }

    /**
     * Fetch a batch of records for the given kind and cursor
     */
    private RecordInfoQueryResult<RecordId> fetchRecordBatch(String kind, String cursor) {
        return queryRepository.getAllRecordIdsFromKind(defaultBatchSize, cursor, kind);
    }
    
    /**
     * Process a batch of records and determine if we should continue processing
     * 
     * @return BatchProcessingResult containing updated cursor, processed records count, and whether to continue
     */
    private BatchProcessingResult processBatchAndUpdateCursor(
            ReplayMessage replayMessage, 
            RecordInfoQueryResult<RecordId> recordIds, 
            long processedRecords) {
        
        // Check if we have records to process in this batch
        boolean hasRecordsInBatch = recordIds != null && 
                                   recordIds.getResults() != null && 
                                   !recordIds.getResults().isEmpty();
        
        String kind = replayMessage.getBody().getKind();
        LOGGER.info(() -> String.format("Fetched %d records for kind: %s", 
            hasRecordsInBatch ? recordIds.getResults().size() : 0, kind));
        
        // Check if we have more pages to fetch
        boolean hasNextPage = recordIds != null && 
                             recordIds.getCursor() != null && 
                             !recordIds.getCursor().isEmpty();
        
        // If we have no records in this batch and no more pages, we're done
        boolean hasMoreRecords = true;
        if (!hasRecordsInBatch && !hasNextPage) {
            LOGGER.info(() -> String.format("No more records found for kind: %s", kind));
            hasMoreRecords = false;
        }
        
        // Process records if we have any
        if (hasRecordsInBatch) {
            // Process the batch of records
            processRecordBatch(replayMessage, recordIds.getResults());
            
            // Update processed records count
            processedRecords += recordIds.getResults().size();
        }
        
        // Update cursor for next batch
        String cursor = recordIds != null ? recordIds.getCursor() : null;
        
        // If we have no next cursor, we're done
        if (cursor == null || cursor.isEmpty()) {
            LOGGER.info(() -> String.format("Reached end of records for kind: %s", kind));
            hasMoreRecords = false;
        }
        
        return new BatchProcessingResult(cursor, processedRecords, hasMoreRecords);
    }
    
    /**
     * Update replay metadata with progress information
     */
    private void updateReplayMetadata(
            AwsReplayMetaDataDTO replayMetaData, 
            long processedRecords, 
            String cursor,
            ReplayRepositoryImpl awsReplayRepository) {
        
        // Update replay metadata with progress and save cursor for potential resume
        replayMetaData.setProcessedRecords(processedRecords);
        replayMetaData.setLastCursor(cursor);
        replayMetaData.setLastUpdatedAt(new Date());
        
        // Calculate and update elapsed time
        if (replayMetaData.getStartedAt() != null) {
            long elapsedMillis = new Date().getTime() - replayMetaData.getStartedAt().getTime();
            String elapsedTime = formatElapsedTime(elapsedMillis);
            replayMetaData.setElapsedTime(elapsedTime);
        }
        
        awsReplayRepository.saveAwsReplayMetaData(replayMetaData);
    }
    
    /**
     * Log the current processing progress
     */
    private void logProcessingProgress(long processedRecords, String kind) {
        String message = String.format("Processed %s records for kind: %s", processedRecords, kind);
        LOGGER.info(message);
    }

    /**
         * Class to hold the result of processing a batch
         */
        private record BatchProcessingResult(@Getter String cursor, @Getter long processedRecords, boolean hasMoreRecords) {
    }
    
    /**
     * Update replay status to completed
     * 
     * @param kind The record kind
     * @param replayId The replay ID
     * @param processedRecords The number of processed records
     */
    private void updateReplayStatusToCompleted(String kind, String replayId, long processedRecords) {
        ReplayRepositoryImpl awsReplayRepository = (ReplayRepositoryImpl) replayRepository;
        AwsReplayMetaDataDTO replayMetaData = awsReplayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId);
        
        if (replayMetaData != null) {
            replayMetaData.setState(ReplayState.COMPLETED.name());
            replayMetaData.setProcessedRecords(processedRecords);
            // Clear the cursor since processing is complete
            replayMetaData.setLastCursor(null);
            replayMetaData.setLastUpdatedAt(new Date());
            
            // Calculate and set elapsed time
            if (replayMetaData.getStartedAt() != null) {
                long elapsedMillis = new Date().getTime() - replayMetaData.getStartedAt().getTime();
                String elapsedTime = formatElapsedTime(elapsedMillis);
                replayMetaData.setElapsedTime(elapsedTime);
            }
            
            awsReplayRepository.saveAwsReplayMetaData(replayMetaData);
        } else {
            LOGGER.warning(() -> String.format("Could not update status to COMPLETED for kind: %s and replayId: %s", kind, replayId));
        }
    }
    
    /**
     * Format elapsed time in a human-readable format
     * 
     * @param millis Elapsed time in milliseconds
     * @return Formatted elapsed time string (e.g., "2h 30m 45s")
     */
    private String formatElapsedTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");
        
        return sb.toString();
    }
    
    /**
     * Process a batch of records for replay.
     * 
     * @param replayMessage The replay message
     * @param records The records to process
     */
    private void processRecordBatch(ReplayMessage replayMessage, List<RecordId> records) {
        String operation = replayMessage.getBody().getOperation();
        
        try {
            // Create record change messages for each record
            List<RecordChangedV2> recordChangedMessages = new ArrayList<>();
            
            // Get the record metadata helper to fetch additional record information
            DynamoDBQueryHelperV2 recordMetadataQueryHelper = getRecordMetadataQueryHelper();
            
            for (RecordId recordId : records) {
                // Fetch the complete record metadata to get additional attributes
                RecordMetadataDoc recordMetadata = recordMetadataQueryHelper.loadByPrimaryKey(
                    RecordMetadataDoc.class, 
                    recordId.getId());
                
                if (recordMetadata == null) {
                    LOGGER.warning(() -> String.format("Record metadata not found for ID: %s", recordId.getId()));
                    continue;
                }
                
                RecordChangedV2 recordChanged = createRecordChangedMessage(recordMetadata, operation);
                recordChangedMessages.add(recordChanged);

                // Publish in batches to avoid exceeding SNS message size limits
                if (recordChangedMessages.size() >= publishBatchSize) {
                    publishRecordChangedMessages(replayMessage, recordChangedMessages);
                    recordChangedMessages.clear();
                }
            }
            
            // Publish any remaining records
            if (!recordChangedMessages.isEmpty()) {
                publishRecordChangedMessages(replayMessage, recordChangedMessages);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error publishing record change messages: %s", e.getMessage()), e);
            // Continue processing other records
        }
    }
    
    /**
     * Create a RecordChangedV2 message from record metadata
     * 
     * @param recordMetadata The record metadata
     * @param operation The operation type
     * @return A RecordChangedV2 message
     */
    private RecordChangedV2 createRecordChangedMessage(RecordMetadataDoc recordMetadata, String operation) {
        RecordChangedV2 recordChanged = new RecordChangedV2();
        recordChanged.setId(recordMetadata.getId());
        recordChanged.setKind(recordMetadata.getKind());
        
        // Set version and modifiedBy from metadata if available
        if (recordMetadata.getMetadata() != null) {
            recordChanged.setVersion(recordMetadata.getMetadata().getLatestVersion());
            recordChanged.setModifiedBy(recordMetadata.getMetadata().getModifyUser());
        }
        
        // Convert string operation to OperationType enum
        OperationType opType = convertToOperationType(operation);
        recordChanged.setOp(opType);
        
        // Set recordBlocks to indicate all blocks are included
        recordChanged.setRecordBlocks(RECORD_BLOCKS);
        
        return recordChanged;
    }
    
    /**
     * Publish record changed messages to the message bus
     * 
     * @param replayMessage The replay message
     * @param recordChangedMessages The list of record changed messages to publish
     */
    private void publishRecordChangedMessages(ReplayMessage replayMessage, List<RecordChangedV2> recordChangedMessages) {
        Optional<CollaborationContext> collaborationContext = getCollaborationContext(replayMessage);
        messageBus.publishMessage(collaborationContext, headers, 
            recordChangedMessages.toArray(new RecordChangedV2[0]));
    }
    
    /**
     * Get the record metadata query helper
     * 
     * @return DynamoDBQueryHelperV2 for record metadata
     */
    private DynamoDBQueryHelperV2 getRecordMetadataQueryHelper() {
        return dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, recordMetadataTableParameterRelativePath,
                workerThreadPool.getClientConfiguration());
    }
    
    /**
     * Extract collaboration context from replay message headers if present
     * 
     * @param replayMessage The replay message
     * @return Optional containing the collaboration context if present
     */
    private Optional<CollaborationContext> getCollaborationContext(ReplayMessage replayMessage) {
        if (replayMessage == null || replayMessage.getHeaders() == null) {
            return Optional.empty();
        }
        
        String collaborationHeader = replayMessage.getHeaders().get(DpsHeaders.COLLABORATION);
        if (collaborationHeader == null || collaborationHeader.isEmpty()) {
            return Optional.empty();
        }
        
        try {
            // Parse the collaboration header
            // Format is typically: id=<id>,application=<application>
            String[] parts = collaborationHeader.split(",");
            if (parts.length < 2) {
                return Optional.empty();
            }
            
            String id = parts[0].substring(parts[0].indexOf('=') + 1);
            String application = parts[1].substring(parts[1].indexOf('=') + 1);
            
            CollaborationContext context = new CollaborationContext();
            context.setId(UUID.fromString(id));
            context.setApplication(application);
            
            return Optional.of(context);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error parsing collaboration context: %s", e.getMessage()), e);
            return Optional.empty();
        }
    }
    
    /**
     * Handles a failure in processing a replay message.
     *
     * @param replayMessage The replay message that failed
     */
    public void processFailure(ReplayMessage replayMessage) {
        if (replayMessage == null || replayMessage.getBody() == null) {
            LOGGER.severe("Cannot process failure for null replay message");
            return;
        }
        
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        LOGGER.log(Level.SEVERE, () -> String.format("Processing failure for replay: %s and kind: %s", replayId, kind));
        
        try {
            // Update kind status to FAILED but preserve the cursor for resume
            ReplayRepositoryImpl awsReplayRepository = (ReplayRepositoryImpl) replayRepository;
            AwsReplayMetaDataDTO kindStatus = awsReplayRepository.getAwsReplayStatusByKindAndReplayId(kind, replayId);
            
            if (kindStatus != null) {
                kindStatus.setState(ReplayState.FAILED.name());
                
                // Calculate and update elapsed time
                if (kindStatus.getStartedAt() != null) {
                    long elapsedMillis = new Date().getTime() - kindStatus.getStartedAt().getTime();
                    String elapsedTime = formatElapsedTime(elapsedMillis);
                    kindStatus.setElapsedTime(elapsedTime);
                }
                
                // Update the timestamp but keep the cursor for resume
                kindStatus.setLastUpdatedAt(new Date());
                
                awsReplayRepository.saveAwsReplayMetaData(kindStatus);
                
                // Log the failure
                auditLogger.createReplayRequestFail(Collections.singletonList(kindStatus.toString()));
            } else {
                LOGGER.log(Level.SEVERE, () -> String.format("Failed to find replay metadata for kind: %s and replayId: %s", kind, replayId));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Error updating failure status: %s", e.getMessage()), e);
        }
    }

    /**
     * Convert string operation to OperationType enum
     *
     * @param operation The operation as a string
     * @return The corresponding OperationType enum value
     */
    private OperationType convertToOperationType(String operation) {
        if (operation == null) {
            LOGGER.warning("Operation is null. Defaulting to update.");
            return OperationType.update;
        }
        
        // Map the operation string to the appropriate OperationType enum value
        return switch (operation.toLowerCase()) {
            case "reindex", "replay" -> OperationType.update;
            default -> {
                LOGGER.warning(() -> String.format("Unknown operation type: %s. Defaulting to update.", operation));
                yield OperationType.update;
            }
        };
    }
}
