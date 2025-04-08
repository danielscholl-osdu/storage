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

import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
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
    
    private final IReplayRepository replayRepository;
    
    private final QueryRepositoryImpl queryRepository;
    
    private final IMessageBus messageBus;
    
    private final DpsHeaders headers;
    
    private final StorageAuditLogger auditLogger;
    
    private final JaxRsDpsLog logger;
    
    @Value("${aws.dynamodb.recordMetadataTable.ssm.relativePath}")
    private String recordMetadataTableParameterRelativePath;
    
    private final DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;
    private final WorkerThreadPool workerThreadPool;

    @Autowired
    public ReplayMessageProcessorAWSImpl(IReplayRepository replayRepository, 
                                        QueryRepositoryImpl queryRepository, 
                                        IMessageBus messageBus, 
                                        DpsHeaders headers, 
                                        StorageAuditLogger auditLogger, 
                                        JaxRsDpsLog logger,
                                        DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory,
                                        WorkerThreadPool workerThreadPool) {
        this.replayRepository = replayRepository;
        this.queryRepository = queryRepository;
        this.messageBus = messageBus;
        this.headers = headers;
        this.auditLogger = auditLogger;
        this.logger = logger;
        this.dynamoDBQueryHelperFactory = dynamoDBQueryHelperFactory;
        this.workerThreadPool = workerThreadPool;
    }

    /**
     * Processes a replay message.
     *
     * @param replayMessage The replay message to process
     */
    public void processReplayMessage(ReplayMessage replayMessage) {
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        try {
            LOGGER.info("Processing replay message for kind: " + kind + " and replayId: " + replayId);
            
            // Update status to IN_PROGRESS
            ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setState(ReplayState.IN_PROGRESS.name());
                replayRepository.save(replayMetaData);
            } else {
                LOGGER.warning("No replay metadata found for kind: " + kind + " and replayId: " + replayId);
            }
            
            // Get record IDs for this kind using getAllRecordIdsFromKind
            // This avoids the "No hash key condition" error by always using a specific kind
            String cursor = null;
            int batchSize = 1000; // Default batch size
            long processedRecords = 0;
            
            do {
                RecordInfoQueryResult<RecordId> recordIds = queryRepository.getAllRecordIdsFromKind(
                    batchSize, 
                    cursor, 
                    kind);
                
                if (recordIds == null || recordIds.getResults() == null || recordIds.getResults().isEmpty()) {
                    LOGGER.info("No more records found for kind: " + kind);
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
                
                LOGGER.info("Processed " + processedRecords + " records for kind: " + kind);
                
            } while (cursor != null && !cursor.isEmpty());
            
            // Update status to COMPLETED
            replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setState(ReplayState.COMPLETED.name());
                replayMetaData.setProcessedRecords(processedRecords);
                replayRepository.save(replayMetaData);
            }
            
            LOGGER.info("Completed processing replay message for kind: " + kind + " and replayId: " + replayId);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing replay message: " + e.getMessage(), e);
            processFailure(replayMessage);
            throw e;
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
            
            for (RecordId record : records) {
                // Fetch the complete record metadata to get additional attributes
                RecordMetadataDoc recordMetadata = recordMetadataQueryHelper.loadByPrimaryKey(
                    RecordMetadataDoc.class, 
                    record.getId());
                
                if (recordMetadata == null) {
                    LOGGER.warning("Record metadata not found for ID: " + record.getId());
                    continue;
                }
                
                // Create the record changed message with all required attributes
                RecordChangedV2 recordChanged = new RecordChangedV2();
                recordChanged.setId(record.getId());
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
                recordChanged.setRecordBlocks("data metadata");
                
                recordChangedMessages.add(recordChanged);

                // Publish in batches of 50 to avoid exceeding SNS message size limits
                if (recordChangedMessages.size() >= 50) {
                    // Get collaboration context from the message headers if present
                    Optional<CollaborationContext> collaborationContext = getCollaborationContext(replayMessage);
                    
                    // Always use the collaboration context method, with empty Optional if no context exists
                    messageBus.publishMessage(collaborationContext, headers, 
                        recordChangedMessages.toArray(new RecordChangedV2[0]));
                    
                    // Clear the batch
                    recordChangedMessages.clear();
                }
            }
            
            // Publish any remaining records
            if (!recordChangedMessages.isEmpty()) {
                // Get collaboration context from the message headers if present
                Optional<CollaborationContext> collaborationContext = getCollaborationContext(replayMessage);
                
                // Always use the collaboration context method, with empty Optional if no context exists
                messageBus.publishMessage(collaborationContext, headers, 
                    recordChangedMessages.toArray(new RecordChangedV2[0]));
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error publishing record change messages: " + e.getMessage(), e);
            // Continue processing other records
        }
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
        if (replayMessage.getHeaders() == null) {
            return Optional.empty();
        }
        
        String collaborationHeader = replayMessage.getHeaders().get("x-collaboration");
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
            LOGGER.log(Level.SEVERE, "Error parsing collaboration context: " + e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Handles a failure in processing a replay message.
     *
     * @param replayMessage The replay message that failed
     */
    public void processFailure(ReplayMessage replayMessage) {
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        LOGGER.log(Level.SEVERE, "Processing failure for replay: " + replayId + " and kind: " + kind);
        
        try {
            // Update kind status to FAILED
            ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (kindStatus != null) {
                kindStatus.setState(ReplayState.FAILED.name());
                replayRepository.save(kindStatus);
            } else {
                LOGGER.log(Level.SEVERE, "Failed to find replay metadata for kind: " + kind + " and replayId: " + replayId);
            }
            
            // Log the failure
            auditLogger.createReplayRequestFail(Collections.singletonList(kindStatus != null ? kindStatus.toString() : "null"));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating failure status: " + e.getMessage(), e);
        }
    }

    /**
     * Convert string operation to OperationType enum
     *
     * @param operation The operation as a string
     * @return The corresponding OperationType enum value
     */
    private OperationType convertToOperationType(String operation) {
        // Map the operation string to the appropriate OperationType enum value
        switch (operation.toLowerCase()) {
            case "create":
                return OperationType.create;
            case "update":
                return OperationType.update;
            case "delete":
                return OperationType.delete;
            case "purge":
                return OperationType.purge;
            case "view":
                return OperationType.view;
            case "create_schema":
                return OperationType.create_schema;
            case "purge_schema":
                return OperationType.purge_schema;
            case "reindex":
                // For reindex operations, we typically want to use update
                return OperationType.update;
            case "replay":
                // For replay operations, we typically want to use update
                return OperationType.update;
            default:
                LOGGER.warning("Unknown operation type: " + operation + ". Defaulting to update.");
                return OperationType.update;
        }
    }
}
