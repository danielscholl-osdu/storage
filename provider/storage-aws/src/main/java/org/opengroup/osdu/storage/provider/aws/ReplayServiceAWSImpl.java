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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayStatus;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AWS implementation of the ReplayService.
 * This class handles the replay API functionality for the AWS provider.
 */
@Primary
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl extends ReplayService {
    
    private final IReplayRepository replayRepository;
    
    private final ReplayMessageHandler messageHandler;
    
    private final QueryRepositoryImpl queryRepository;
    
    private final DpsHeaders headers;
    
    private final StorageAuditLogger auditLogger;
    
    private final JaxRsDpsLog logger;
    
    @Value("#{${replay.operation.routingProperties}}")
    private Map<String, Map<String, String>> replayOperationRoutingProperties;
    
    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;

    public ReplayServiceAWSImpl(IReplayRepository replayRepository, ReplayMessageHandler messageHandler, QueryRepositoryImpl queryRepository, IMessageBus messageBus, DpsHeaders headers, StorageAuditLogger auditLogger, JaxRsDpsLog logger, ObjectMapper objectMapper) {
        this.replayRepository = replayRepository;
        this.messageHandler = messageHandler;
        this.queryRepository = queryRepository;
        this.headers = headers;
        this.auditLogger = auditLogger;
        this.logger = logger;
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
     * This method delegates to the ReplayMessageProcessorAWSImpl through ReplayMessageHandler.
     *
     * @param replayMessage The replay message to process
     */
    @Override
    public void processReplayMessage(ReplayMessage replayMessage) {
        // This method should not be called directly in AWS implementation
        // It's here to satisfy the interface requirements
        logger.error("ReplayServiceAWSImpl.processReplayMessage called directly. This should be handled by ReplayMessageHandler instead.");
        throw new UnsupportedOperationException("This method should be called on ReplayMessageHandler");
    }
    
    /**
     * Handles a failure in processing a replay message.
     * This method delegates to the ReplayMessageProcessorAWSImpl through ReplayMessageHandler.
     *
     * @param replayMessage The replay message that failed
     */
    @Override
    public void processFailure(ReplayMessage replayMessage) {
        // This method should not be called directly in AWS implementation
        // It's here to satisfy the interface requirements
        logger.error("ReplayServiceAWSImpl.processFailure called directly. This should be handled by ReplayMessageHandler instead.");
        throw new UnsupportedOperationException("This method should be called on ReplayMessageHandler");
    }
}
