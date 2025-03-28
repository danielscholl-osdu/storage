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
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.ReplayStateEnum;
import org.opengroup.osdu.storage.service.audit.StorageAuditLogger;
import org.opengroup.osdu.storage.service.replay.IReplayRepository;
import org.opengroup.osdu.storage.service.replay.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.service.replay.ReplayService;
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
    
    @Autowired
    private IReplayRepository replayRepository;
    
    @Autowired
    private ReplayMessageHandler messageHandler;
    
    @Autowired
    private QueryRepositoryImpl queryRepository;
    
    @Autowired
    private IMessageBus messageBus;
    
    @Autowired
    private DpsHeaders headers;
    
    @Autowired
    private StorageAuditLogger auditLogger;
    
    @Autowired
    private JaxRsDpsLog logger;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("#{${replay.operation.routingProperties}}")
    private Map<String, Map<String, String>> replayOperationRoutingProperties;
    
    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;
    
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
        
        // Find the overall status
        Optional<ReplayMetaDataDTO> overallStatus = replayMetaDataList.stream()
                .filter(r -> "overall".equals(r.getId()))
                .findFirst();
        
        if (!overallStatus.isPresent()) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Invalid replay data", 
                    "No overall status found for replay ID: " + replayId);
        }
        
        ReplayMetaDataDTO overall = overallStatus.get();
        
        // Get kind-specific statuses
        List<ReplayMetaDataDTO> kindStatuses = replayMetaDataList.stream()
                .filter(r -> !"overall".equals(r.getId()))
                .collect(Collectors.toList());
        
        // Build the response
        ReplayStatusResponse response = new ReplayStatusResponse();
        response.setReplayId(replayId);
        response.setOperation(overall.getOperation());
        response.setState(overall.getState());
        response.setStartedAt(overall.getStartedAt());
        response.setElapsedTime(overall.getElapsedTime());
        response.setTotalRecords(overall.getTotalRecords());
        response.setProcessedRecords(overall.getProcessedRecords());
        
        // Add kind-specific statuses
        Map<String, Map<String, Object>> kindStatusMap = new HashMap<>();
        for (ReplayMetaDataDTO kindStatus : kindStatuses) {
            Map<String, Object> statusMap = new HashMap<>();
            statusMap.put("state", kindStatus.getState());
            statusMap.put("totalRecords", kindStatus.getTotalRecords());
            statusMap.put("processedRecords", kindStatus.getProcessedRecords());
            
            kindStatusMap.put(kindStatus.getKind(), statusMap);
        }
        response.setKindStatus(kindStatusMap);
        
        return response;
    }
    
    /**
     * Processes a replay message.
     *
     * @param replayMessage The replay message to process
     */
    @Override
    public void processReplayMessage(ReplayMessage replayMessage) {
        String replayId = replayMessage.getReplayId();
        String kind = replayMessage.getKind();
        String operation = replayMessage.getOperation();
        
        logger.info("Processing replay message: " + replayId + " for kind: " + kind + " with operation: " + operation);
        
        try {
            // Update status to IN_PROGRESS if it's not already
            ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (kindStatus != null && !ReplayStateEnum.IN_PROGRESS.name().equals(kindStatus.getState())) {
                kindStatus.setState(ReplayStateEnum.IN_PROGRESS.name());
                replayRepository.save(kindStatus);
            }
            
            // Get routing properties for the operation
            Map<String, String> routingProps = replayOperationRoutingProperties.get(operation);
            if (routingProps == null) {
                throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Invalid operation", 
                        "No routing properties found for operation: " + operation);
            }
            
            // Get batch sizes
            int queryBatchSize = Integer.parseInt(routingProps.getOrDefault("queryBatchSize", "5000"));
            int publisherBatchSize = Integer.parseInt(routingProps.getOrDefault("publisherBatchSize", "50"));
            
            // Process records in batches
            String cursor = null;
            long processedCount = 0;
            
            do {
                // Query for records
                RecordInfoQueryResult<RecordId> queryResult = queryRepository.getAllRecordIdsFromKind(queryBatchSize, cursor, kind);
                List<RecordId> recordIds = queryResult.getRecords();
                cursor = queryResult.getCursor();
                
                if (recordIds != null && !recordIds.isEmpty()) {
                    // Process records in smaller batches for publishing
                    for (int i = 0; i < recordIds.size(); i += publisherBatchSize) {
                        int endIndex = Math.min(i + publisherBatchSize, recordIds.size());
                        List<RecordId> batch = recordIds.subList(i, endIndex);
                        
                        // Publish record change messages
                        publishRecordChanges(batch, operation, routingProps);
                        
                        // Update processed count
                        processedCount += batch.size();
                        
                        // Update status periodically
                        if (processedCount % 1000 == 0 || cursor == null) {
                            updateReplayStatus(replayId, kind, processedCount);
                        }
                    }
                }
            } while (cursor != null);
            
            // Update final status
            updateReplayStatus(replayId, kind, processedCount);
            
            // Mark as completed
            kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            kindStatus.setState(ReplayStateEnum.COMPLETED.name());
            replayRepository.save(kindStatus);
            
            // Check if all kinds are completed
            checkAndUpdateOverallStatus(replayId);
            
            logger.info("Completed processing replay message: " + replayId + " for kind: " + kind);
            
        } catch (Exception e) {
            logger.error("Error processing replay message: " + e.getMessage(), e);
            processFailure(replayMessage);
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error processing replay", 
                    "Failed to process replay for kind: " + kind + ", error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles a failure in processing a replay message.
     *
     * @param replayMessage The replay message that failed
     */
    @Override
    public void processFailure(ReplayMessage replayMessage) {
        String replayId = replayMessage.getReplayId();
        String kind = replayMessage.getKind();
        
        logger.error("Processing failure for replay: " + replayId + " and kind: " + kind);
        
        try {
            // Update kind status to FAILED
            ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
            if (kindStatus != null) {
                kindStatus.setState(ReplayStateEnum.FAILED.name());
                replayRepository.save(kindStatus);
            }
            
            // Update overall status
            ReplayMetaDataDTO overallStatus = replayRepository.getReplayStatusByKindAndReplayId("overall", replayId);
            if (overallStatus != null) {
                overallStatus.setState(ReplayStateEnum.FAILED.name());
                replayRepository.save(overallStatus);
            }
            
            // Log the failure
            auditLogger.logReplayFailure(replayId, kind);
            
        } catch (Exception e) {
            logger.error("Error updating failure status: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles a replay request.
     *
     * @param replayRequest The replay request
     * @return The response to the replay request
     */
    @Override
    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {
        String replayId = replayRequest.getReplayId();
        String operation = replayRequest.getOperation();
        List<String> kinds = replayRequest.getKinds();
        
        logger.info("Handling replay request: " + replayId + " for operation: " + operation);
        
        try {
            // Validate operation
            if (!replayOperationRoutingProperties.containsKey(operation)) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid operation", 
                        "Operation not supported: " + operation);
            }
            
            // Initialize overall status
            ReplayMetaDataDTO overallStatus = new ReplayMetaDataDTO();
            overallStatus.setId("overall");
            overallStatus.setReplayId(replayId);
            overallStatus.setOperation(operation);
            overallStatus.setState(ReplayStateEnum.QUEUED.name());
            overallStatus.setStartedAt(new Date());
            overallStatus.setProcessedRecords(0L);
            
            // Serialize filter if present
            if (replayRequest.getFilter() != null) {
                try {
                    overallStatus.setFilter(objectMapper.writeValueAsString(replayRequest.getFilter()));
                } catch (JsonProcessingException e) {
                    throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid filter", 
                            "Failed to serialize filter: " + e.getMessage(), e);
                }
            }
            
            // If no kinds specified, get all kinds
            if (kinds == null || kinds.isEmpty()) {
                Map<String, Long> kindCounts = queryRepository.getActiveRecordsCount();
                kinds = new ArrayList<>(kindCounts.keySet());
                
                // Set total records count
                long totalRecords = kindCounts.values().stream().mapToLong(Long::longValue).sum();
                overallStatus.setTotalRecords(totalRecords);
            } else {
                // Get counts for specified kinds
                Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(kinds);
                long totalRecords = kindCounts.values().stream().mapToLong(Long::longValue).sum();
                overallStatus.setTotalRecords(totalRecords);
            }
            
            // Save overall status
            replayRepository.save(overallStatus);
            
            // Create and save status for each kind
            for (String kind : kinds) {
                ReplayMetaDataDTO kindStatus = new ReplayMetaDataDTO();
                kindStatus.setId(kind);
                kindStatus.setKind(kind);
                kindStatus.setReplayId(replayId);
                kindStatus.setOperation(operation);
                kindStatus.setState(ReplayStateEnum.QUEUED.name());
                kindStatus.setStartedAt(new Date());
                kindStatus.setProcessedRecords(0L);
                
                // Get record count for this kind
                Map<String, Long> kindCount = queryRepository.getActiveRecordsCountForKinds(Collections.singletonList(kind));
                kindStatus.setTotalRecords(kindCount.getOrDefault(kind, 0L));
                
                replayRepository.save(kindStatus);
                
                // Create and send replay message
                ReplayMessage message = new ReplayMessage();
                message.setReplayId(replayId);
                message.setKind(kind);
                message.setOperation(operation);
                if (replayRequest.getFilter() != null) {
                    message.setFilter(replayRequest.getFilter());
                }
                
                messageHandler.sendReplayMessage(Collections.singletonList(message), operation);
            }
            
            // Create response
            ReplayResponse response = new ReplayResponse();
            response.setReplayId(replayId);
            response.setOperation(operation);
            response.setStatus(ReplayStateEnum.QUEUED.name());
            
            // Log the request
            auditLogger.logReplayRequest(replayId, operation, kinds);
            
            return response;
            
        } catch (Exception e) {
            logger.error("Error handling replay request: " + e.getMessage(), e);
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error handling replay request", 
                    "Failed to process replay request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates the status of a replay operation for a specific kind.
     *
     * @param replayId The unique identifier for the replay operation
     * @param kind The kind of records being replayed
     * @param processedCount The number of records processed so far
     */
    private void updateReplayStatus(String replayId, String kind, long processedCount) {
        // Update kind status
        ReplayMetaDataDTO kindStatus = replayRepository.getReplayStatusByKindAndReplayId(kind, replayId);
        if (kindStatus != null) {
            kindStatus.setProcessedRecords(processedCount);
            // Calculate elapsed time
            long elapsedMillis = System.currentTimeMillis() - kindStatus.getStartedAt().getTime();
            kindStatus.setElapsedTime(formatElapsedTime(elapsedMillis));
            replayRepository.save(kindStatus);
        }
        
        // Update overall status
        updateOverallProcessedCount(replayId);
    }
    
    /**
     * Updates the overall processed count for a replay operation.
     *
     * @param replayId The unique identifier for the replay operation
     */
    private void updateOverallProcessedCount(String replayId) {
        List<ReplayMetaDataDTO> allStatuses = replayRepository.getReplayStatusByReplayId(replayId);
        
        // Find kind-specific statuses
        List<ReplayMetaDataDTO> kindStatuses = allStatuses.stream()
                .filter(r -> !"overall".equals(r.getId()))
                .collect(Collectors.toList());
        
        // Calculate total processed count
        long totalProcessed = kindStatuses.stream()
                .mapToLong(ReplayMetaDataDTO::getProcessedRecords)
                .sum();
        
        // Update overall status
        ReplayMetaDataDTO overallStatus = replayRepository.getReplayStatusByKindAndReplayId("overall", replayId);
        if (overallStatus != null) {
            overallStatus.setProcessedRecords(totalProcessed);
            // Calculate elapsed time
            long elapsedMillis = System.currentTimeMillis() - overallStatus.getStartedAt().getTime();
            overallStatus.setElapsedTime(formatElapsedTime(elapsedMillis));
            replayRepository.save(overallStatus);
        }
    }
    
    /**
     * Checks if all kinds are completed and updates the overall status accordingly.
     *
     * @param replayId The unique identifier for the replay operation
     */
    private void checkAndUpdateOverallStatus(String replayId) {
        List<ReplayMetaDataDTO> allStatuses = replayRepository.getReplayStatusByReplayId(replayId);
        
        // Find kind-specific statuses
        List<ReplayMetaDataDTO> kindStatuses = allStatuses.stream()
                .filter(r -> !"overall".equals(r.getId()))
                .collect(Collectors.toList());
        
        // Check if all kinds are completed
        boolean allCompleted = kindStatuses.stream()
                .allMatch(r -> ReplayStateEnum.COMPLETED.name().equals(r.getState()));
        
        // Check if any kind failed
        boolean anyFailed = kindStatuses.stream()
                .anyMatch(r -> ReplayStateEnum.FAILED.name().equals(r.getState()));
        
        // Update overall status
        ReplayMetaDataDTO overallStatus = replayRepository.getReplayStatusByKindAndReplayId("overall", replayId);
        if (overallStatus != null) {
            if (allCompleted) {
                overallStatus.setState(ReplayStateEnum.COMPLETED.name());
            } else if (anyFailed) {
                overallStatus.setState(ReplayStateEnum.FAILED.name());
            }
            replayRepository.save(overallStatus);
        }
    }
    
    /**
     * Publishes record change messages for a batch of records.
     *
     * @param recordIds The record IDs to publish changes for
     * @param operation The operation being performed
     * @param routingProps The routing properties for the operation
     */
    private void publishRecordChanges(List<RecordId> recordIds, String operation, Map<String, String> routingProps) {
        // Get the queue URL from routing properties
        String queueUrl = routingProps.get("queue");
        if (queueUrl == null) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Invalid routing properties", 
                    "No queue URL found for operation: " + operation);
        }
        
        // Create routing info map
        Map<String, String> routingInfo = new HashMap<>();
        routingInfo.put("queue", queueUrl);
        
        // Create record change messages
        List<Map<String, Object>> recordChanges = new ArrayList<>();
        for (RecordId recordId : recordIds) {
            Map<String, Object> recordChange = new HashMap<>();
            recordChange.put("id", recordId.getId());
            recordChange.put("kind", recordId.getKind());
            recordChange.put("op", "update");
            recordChanges.add(recordChange);
        }
        
        // Publish messages
        messageBus.publishMessage(headers, routingInfo, recordChanges);
    }
    
    /**
     * Formats elapsed time in milliseconds to a human-readable string.
     *
     * @param elapsedMillis The elapsed time in milliseconds
     * @return A formatted string representing the elapsed time
     */
    private String formatElapsedTime(long elapsedMillis) {
        long seconds = elapsedMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds %= 60;
        minutes %= 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
