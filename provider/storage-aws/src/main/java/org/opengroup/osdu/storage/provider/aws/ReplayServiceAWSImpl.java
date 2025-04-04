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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayStatus;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AWS implementation of the ReplayService.
 * This class handles the replay functionality for the AWS provider.
 */
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl extends ReplayService {
    
    private final IReplayRepository replayRepository;
    
    private final ReplayMessageHandler messageHandler;
    
    private final QueryRepositoryImpl queryRepository;
    
    private final IMessageBus messageBus;
    
    private final DpsHeaders headers;
    
    private final StorageAuditLogger auditLogger;
    
    private final JaxRsDpsLog logger;
    
    private final ObjectMapper objectMapper;
    
    @Value("#{${replay.operation.routingProperties}}")
    private Map<String, Map<String, String>> replayOperationRoutingProperties;
    
    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;

    public ReplayServiceAWSImpl(IReplayRepository replayRepository, ReplayMessageHandler messageHandler, QueryRepositoryImpl queryRepository, IMessageBus messageBus, DpsHeaders headers, StorageAuditLogger auditLogger, JaxRsDpsLog logger, ObjectMapper objectMapper) {
        this.replayRepository = replayRepository;
        this.messageHandler = messageHandler;
        this.queryRepository = queryRepository;
        this.messageBus = messageBus;
        this.headers = headers;
        this.auditLogger = auditLogger;
        this.logger = logger;
        this.objectMapper = objectMapper;
    }

    /**
     * Gets the status of a replay operation.
     *
     * @param replayId The unique identifier for the replay operation
     * @return The status of the replay operation
     */
    @Override
    public ReplayStatusResponse getReplayStatus(String replayId) {
        List<ReplayMetaDataDTO> replayMetaDataList = replayRepository.getReplayStatusByReplayId(replayId);
        
        if (replayMetaDataList == null || replayMetaDataList.isEmpty()) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, "Replay not found", 
                    "No replay found with ID: " + replayId);
        }
        
        // Calculate overall status
        ReplayState overallState = calculateOverallState(replayMetaDataList);
        long totalRecords = replayMetaDataList.stream().mapToLong(ReplayMetaDataDTO::getTotalRecords).sum();
        long processedRecords = replayMetaDataList.stream().mapToLong(ReplayMetaDataDTO::getProcessedRecords).sum();
        
        // Get the first record to extract common fields
        ReplayMetaDataDTO firstRecord = replayMetaDataList.get(0);
        
        // Create the response
        ReplayStatusResponse response = new ReplayStatusResponse();
        response.setReplayId(replayId);
        response.setOperation(firstRecord.getOperation());
        response.setOverallState(overallState.name());
        response.setStartedAt(firstRecord.getStartedAt());
        response.setElapsedTime(firstRecord.getElapsedTime());
        response.setTotalRecords(totalRecords);
        response.setProcessedRecords(processedRecords);
        response.setFilter(firstRecord.getFilter());
        
        // Convert ReplayMetaDataDTO objects to ReplayStatus objects
        List<ReplayStatus> statusList = new ArrayList<>();
        for (ReplayMetaDataDTO dto : replayMetaDataList) {
            ReplayStatus status = new ReplayStatus();
            status.setKind(dto.getKind());
            status.setState(dto.getState());
            status.setTotalRecords(dto.getTotalRecords());
            status.setProcessedRecords(dto.getProcessedRecords());
            status.setStartedAt(dto.getStartedAt());
            status.setElapsedTime(dto.getElapsedTime());
            statusList.add(status);
        }
        response.setStatus(statusList);
        
        return response;
    }
    
    private ReplayState calculateOverallState(List<ReplayMetaDataDTO> replayMetaDataList) {
        // If any kind is FAILED, the overall state is FAILED
        if (replayMetaDataList.stream().anyMatch(dto -> ReplayState.FAILED.name().equals(dto.getState()))) {
            return ReplayState.FAILED;
        }
        
        // If any kind is IN_PROGRESS, the overall state is IN_PROGRESS
        if (replayMetaDataList.stream().anyMatch(dto -> ReplayState.IN_PROGRESS.name().equals(dto.getState()))) {
            return ReplayState.IN_PROGRESS;
        }
        
        // If all kinds are COMPLETED, the overall state is COMPLETED
        if (replayMetaDataList.stream().allMatch(dto -> ReplayState.COMPLETED.name().equals(dto.getState()))) {
            return ReplayState.COMPLETED;
        }
        
        // Default to QUEUED
        return ReplayState.QUEUED;
    }
    
    /**
     * Handles a replay request by creating individual records for each kind.
     *
     * @param replayRequest The replay request
     * @return The response to the replay request
     */
    @Override
    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {
        // Generate a unique replay ID if not provided
        if (replayRequest.getReplayId() == null || replayRequest.getReplayId().isEmpty()) {
            replayRequest.setReplayId(UUID.randomUUID().toString());
        }
        String replayId = replayRequest.getReplayId();
        
        // Get the list of kinds to replay
        List<String> kinds;
        if (replayRequest.getFilter() != null && replayRequest.getFilter().getKinds() != null && !replayRequest.getFilter().getKinds().isEmpty()) {
            kinds = replayRequest.getFilter().getKinds();
        } else {
            // If no kinds specified, get all kinds
            Map<String, Long> kindCounts = queryRepository.getActiveRecordsCount();
            kinds = new ArrayList<>(kindCounts.keySet());
        }
        
        // Create a replay metadata record for EACH kind
        for (String kind : kinds) {
            ReplayMetaDataDTO replayMetaData = new ReplayMetaDataDTO();
            replayMetaData.setId(kind);  // Use kind as the ID (hash key)
            replayMetaData.setReplayId(replayId);
            replayMetaData.setKind(kind);
            replayMetaData.setOperation(replayRequest.getOperation());
            replayMetaData.setState(ReplayState.QUEUED.name());
            replayMetaData.setStartedAt(new Date());
            
            // Get count of records for this kind
            Map<String, Long> kindCount = queryRepository.getActiveRecordsCountForKinds(Collections.singletonList(kind));
            Long count = kindCount.getOrDefault(kind, 0L);
            replayMetaData.setTotalRecords(count);
            replayMetaData.setProcessedRecords(0L);
            
            // Save the replay metadata for this kind
            replayRepository.save(replayMetaData);
        }
        
        // Create and publish replay messages - one message per kind
        List<ReplayMessage> messages = createReplayMessages(replayRequest, replayId, kinds);
        messageHandler.sendReplayMessage(messages, replayRequest.getOperation());
        
        // Log success
        auditLogger.createReplayRequestSuccess(Collections.singletonList("Replay started for ID: " + replayId));
        
        return new ReplayResponse(replayId);
    }
    
    /**
     * Creates replay messages for the specified kinds.
     *
     * @param replayRequest The replay request
     * @param replayId The unique identifier for the replay operation
     * @param kinds The kinds of records to replay
     * @return A list of replay messages
     */
    private List<ReplayMessage> createReplayMessages(ReplayRequest replayRequest, String replayId, List<String> kinds) {
        List<ReplayMessage> messages = new ArrayList<>();
        int kindCounter = 0;
        
        for (String kind : kinds) {
            // Get count of records for this kind
            Map<String, Long> kindCount = queryRepository.getActiveRecordsCountForKinds(Collections.singletonList(kind));
            Long count = kindCount.getOrDefault(kind, 0L);
            
            // Create the replay data
            org.opengroup.osdu.storage.dto.ReplayData body = new org.opengroup.osdu.storage.dto.ReplayData();
            body.setId(UUID.randomUUID().toString());
            body.setReplayId(replayId);
            body.setKind(kind);
            body.setOperation(replayRequest.getOperation());
            body.setTotalCount(count);
            body.setCompletionCount(0L);
            // Always use REPLAY_KIND type even for REPLAY_ALL operations
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
     * Processes a replay message.
     *
     * @param replayMessage The replay message to process
     */
    @Override
    public void processReplayMessage(ReplayMessage replayMessage) {
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        try {
            logger.info("Processing replay message for kind: " + kind + " and replayId: " + replayId);
            
            // Update status to IN_PROGRESS
            ReplayMetaDataDTO replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setState(ReplayState.IN_PROGRESS.name());
                replayRepository.save(replayMetaData);
            } else {
                logger.warning("No replay metadata found for kind: " + kind + " and replayId: " + replayId);
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
                    logger.info("No more records found for kind: " + kind);
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
                
                logger.info("Processed " + processedRecords + " records for kind: " + kind);
                
            } while (cursor != null && !cursor.isEmpty());
            
            // Update status to COMPLETED
            replayMetaData = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (replayMetaData != null) {
                replayMetaData.setState(ReplayState.COMPLETED.name());
                replayMetaData.setProcessedRecords(processedRecords);
                replayRepository.save(replayMetaData);
            }
            
            logger.info("Completed processing replay message for kind: " + kind + " and replayId: " + replayId);
            
        } catch (Exception e) {
            logger.error("Error processing replay message: " + e.getMessage(), e);
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
            // Get the routing properties for this operation
            Map<String, String> routingInfo = replayOperationRoutingProperties.get(operation);
            if (routingInfo == null) {
                logger.error("No routing properties found for operation: " + operation);
                return;
            }
            
            // Create record change messages for each record
            List<RecordChangedV2> recordChangedMessages = new ArrayList<>();
            
            for (RecordId record : records) {
                RecordChangedV2 recordChanged = new RecordChangedV2();
                recordChanged.setId(record.getId());
                
                // Convert string operation to OperationType enum
                OperationType opType = convertToOperationType(operation);
                recordChanged.setOp(opType);
                
                recordChangedMessages.add(recordChanged);
                
                // Publish in batches of 50 to avoid exceeding SNS message size limits
                if (recordChangedMessages.size() >= 50) {
                    // Get collaboration context from the message headers if present
                    Optional<CollaborationContext> collaborationContext = getCollaborationContext(replayMessage);
                    
                    // Publish the batch
                    if (collaborationContext.isPresent()) {
                        messageBus.publishMessage(collaborationContext, headers, 
                            recordChangedMessages.toArray(new RecordChangedV2[0]));
                    } else {
                        // Use the routing info approach for non-collaboration messages
                        messageBus.publishMessage(headers, routingInfo, recordChangedMessages);
                    }
                    
                    // Clear the batch
                    recordChangedMessages.clear();
                }
            }
            
            // Publish any remaining records
            if (!recordChangedMessages.isEmpty()) {
                // Get collaboration context from the message headers if present
                Optional<CollaborationContext> collaborationContext = getCollaborationContext(replayMessage);
                
                // Publish the batch
                if (collaborationContext.isPresent()) {
                    messageBus.publishMessage(collaborationContext, headers, 
                        recordChangedMessages.toArray(new RecordChangedV2[0]));
                } else {
                    // Use the routing info approach for non-collaboration messages
                    messageBus.publishMessage(headers, routingInfo, recordChangedMessages);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error publishing record change messages: " + e.getMessage(), e);
            // Continue processing other records
        }
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
            logger.error("Error parsing collaboration context: " + e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Handles a failure in processing a replay message.
     *
     * @param replayMessage The replay message that failed
     */
    @Override
    public void processFailure(ReplayMessage replayMessage) {
        String replayId = replayMessage.getBody().getReplayId();
        String kind = replayMessage.getBody().getKind();
        
        logger.error("Processing failure for replay: " + replayId + " and kind: " + kind);
        
        try {
            // Update kind status to FAILED
            ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (kindStatus != null) {
                kindStatus.setState(ReplayState.FAILED.name());
                replayRepository.save(kindStatus);
            } else {
                logger.error("Failed to find replay metadata for kind: " + kind + " and replayId: " + replayId);
            }
            
            // Log the failure
            auditLogger.createReplayRequestFail(Collections.singletonList(kindStatus != null ? kindStatus.toString() : "null"));
        } catch (Exception e) {
            logger.error("Error updating failure status: " + e.getMessage(), e);
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
                logger.warning("Unknown operation type: " + operation + ". Defaulting to update.");
                return OperationType.update;
        }
    }

}
