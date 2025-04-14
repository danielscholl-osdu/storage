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

package org.opengroup.osdu.storage.provider.aws;

import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
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
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int PUBLISH_BATCH_SIZE = 50;
    private static final String RECORD_BLOCKS = "data metadata";
    private static final String COLLABORATION_HEADER = "x-collaboration";
    
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
            
            // Update status to IN_PROGRESS
            ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setState(ReplayState.IN_PROGRESS.name());
                replayRepository.save(replayMetaData);
            } else {
                LOGGER.warning(() -> String.format("No replay metadata found for kind: %s and replayId: %s", kind, replayId));
                return;
            }
            
            processRecordsInBatches(replayMessage, replayMetaData);
            
            // Update status to COMPLETED
            updateReplayStatusToCompleted(kind, replayId, replayMetaData.getProcessedRecords());
            
            LOGGER.info(() -> String.format("Completed processing replay message for kind: %s and replayId: %s", kind, replayId));
            
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
    private void processRecordsInBatches(ReplayMessage replayMessage, ReplayMetaDataDTO replayMetaData) {
        String kind = replayMessage.getBody().getKind();
        String cursor = null;
        long processedRecords = 0;
        
        do {
            RecordInfoQueryResult<RecordId> recordIds = queryRepository.getAllRecordIdsFromKind(
                DEFAULT_BATCH_SIZE, 
                cursor, 
                kind);
            
            if (recordIds == null || recordIds.getResults() == null || recordIds.getResults().isEmpty()) {
                LOGGER.info(() -> String.format("No more records found for kind: %s", kind));
                break;
            }
            
            // Process the batch of records
            processRecordBatch(replayMessage, recordIds.getResults());
            
            // Update cursor for next batch
            cursor = recordIds.getCursor();
            
            // Update processed records count
            processedRecords += recordIds.getResults().size();
            
            // Update replay metadata with progress
            if (replayMetaData != null) {
                replayMetaData.setProcessedRecords(processedRecords);
                replayRepository.save(replayMetaData);
            }

            String message = String.format("Processed %s records for kind: %s", processedRecords, kind);
            LOGGER.info(message);
        } while (cursor != null && !cursor.isEmpty());
    }
    
    /**
     * Update replay status to completed
     * 
     * @param kind The record kind
     * @param replayId The replay ID
     * @param processedRecords The number of processed records
     */
    private void updateReplayStatusToCompleted(String kind, String replayId, long processedRecords) {
        ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
        if (replayMetaData != null) {
            replayMetaData.setState(ReplayState.COMPLETED.name());
            replayMetaData.setProcessedRecords(processedRecords);
            replayRepository.save(replayMetaData);
        } else {
            LOGGER.warning(() -> String.format("Could not update status to COMPLETED for kind: %s and replayId: %s", kind, replayId));
        }
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
                if (recordChangedMessages.size() >= PUBLISH_BATCH_SIZE) {
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
        
        String collaborationHeader = replayMessage.getHeaders().get(COLLABORATION_HEADER);
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
            // Update kind status to FAILED
            ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (kindStatus != null) {
                kindStatus.setState(ReplayState.FAILED.name());
                replayRepository.save(kindStatus);
                
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
