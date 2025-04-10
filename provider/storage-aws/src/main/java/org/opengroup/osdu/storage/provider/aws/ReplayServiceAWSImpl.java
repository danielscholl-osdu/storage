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

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayStatus;
import org.opengroup.osdu.storage.enums.ReplayOperation;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AWS implementation of the ReplayService.
 * This class handles the replay API functionality for the AWS provider.
 */
@Primary
@Service
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplayServiceAWSImpl extends ReplayService {
    private static final Logger LOGGER = Logger.getLogger(ReplayServiceAWSImpl.class.getName());
    
    private final IReplayRepository replayRepository;
    private final QueryRepositoryImpl queryRepository;
    private final DpsHeaders headers;
    private final StorageAuditLogger auditLogger;
    private final JaxRsDpsLog logger;
    private final ParallelReplayProcessor parallelReplayProcessor;
    private final ExecutorService executorService;
    private final RequestScopeUtil requestScopeUtil;

    public ReplayServiceAWSImpl(
            IReplayRepository replayRepository,
            QueryRepositoryImpl queryRepository,
            DpsHeaders headers, 
            StorageAuditLogger auditLogger, 
            JaxRsDpsLog logger,
            ParallelReplayProcessor parallelReplayProcessor,
            ExecutorService replayExecutorService,
            RequestScopeUtil requestScopeUtil) {
        this.replayRepository = replayRepository;
        this.queryRepository = queryRepository;
        this.headers = headers;
        this.auditLogger = auditLogger;
        this.logger = logger;
        this.parallelReplayProcessor = parallelReplayProcessor;
        this.executorService = replayExecutorService;
        this.requestScopeUtil = requestScopeUtil;
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
                    "The replay ID " + replayId + " is invalid.");
        }
        
        // Check if we only have the initial system record
        boolean hasOnlySystemRecord = replayMetaDataList.size() == 1 && 
                                     "system".equals(replayMetaDataList.get(0).getKind());
        
        // Calculate overall status - we don't need special handling here anymore
        // as calculateOverallState now properly handles the system record
        ReplayState overallState = calculateOverallState(replayMetaDataList);
        
        // For system-only records, we don't have real counts yet
        long totalRecords = hasOnlySystemRecord ? 0 : 
                replayMetaDataList.stream()
                    .filter(dto -> !"system".equals(dto.getKind()))
                    .mapToLong(ReplayMetaDataDTO::getTotalRecords)
                    .sum();
                    
        long processedRecords = hasOnlySystemRecord ? 0 : 
                replayMetaDataList.stream()
                    .filter(dto -> !"system".equals(dto.getKind()))
                    .mapToLong(ReplayMetaDataDTO::getProcessedRecords)
                    .sum();
        
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
            // Skip the system record in the detailed status list
            if ("system".equals(dto.getKind())) {
                continue;
            }
            
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
        // Filter out the system record for status calculation
        List<ReplayMetaDataDTO> actualKindRecords = replayMetaDataList.stream()
                .filter(dto -> !"system".equals(dto.getKind()))
                .toList();
        
        // If there are no actual kind records, use the system record status
        if (actualKindRecords.isEmpty()) {
            return ReplayState.valueOf(replayMetaDataList.get(0).getState());
        }
        
        // If any kind is FAILED, the overall state is FAILED
        if (actualKindRecords.stream().anyMatch(dto -> ReplayState.FAILED.name().equals(dto.getState()))) {
            return ReplayState.FAILED;
        }
        
        // If any kind is IN_PROGRESS, the overall state is IN_PROGRESS
        if (actualKindRecords.stream().anyMatch(dto -> ReplayState.IN_PROGRESS.name().equals(dto.getState()))) {
            return ReplayState.IN_PROGRESS;
        }
        
        // If any kind is QUEUED, the overall state is QUEUED
        if (actualKindRecords.stream().anyMatch(dto -> ReplayState.QUEUED.name().equals(dto.getState()))) {
            return ReplayState.QUEUED;
        }
        
        // If all kinds are COMPLETED, the overall state is COMPLETED
        if (actualKindRecords.stream().allMatch(dto -> ReplayState.COMPLETED.name().equals(dto.getState()))) {
            return ReplayState.COMPLETED;
        }
        
        // Default to QUEUED
        return ReplayState.QUEUED;
    }
    
    /**
     * Handles a replay request by creating individual records for each kind.
     * This implementation uses asynchronous parallel processing.
     *
     * @param replayRequest The replay request
     * @return The response to the replay request
     */
    @Override
    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {
        LOGGER.info("Handling replay request with ID: " + replayRequest.getReplayId());
        
        // Validate operation type
        Set<String> validReplayOperation = ReplayOperation.getValidReplayOperations();
        boolean isValidReplayOperation = validReplayOperation.contains(replayRequest.getOperation());
        
        if (!isValidReplayOperation) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST,
                    "Validation Error", "Not a valid operation. The valid operations are: " + validReplayOperation);
        }
        
        // Generate a unique replay ID if not provided
        if (replayRequest.getReplayId() == null || replayRequest.getReplayId().isEmpty()) {
            replayRequest.setReplayId(UUID.randomUUID().toString());
        }
        String replayId = replayRequest.getReplayId();
        
        // Create an initial status record immediately so users can query status right away
        createInitialStatusRecord(replayId, replayRequest.getOperation(), replayRequest.getFilter());
        
        // Get the list of kinds to replay - MOVED TO ASYNC PROCESSING
        List<String> kinds;
        if (replayRequest.getFilter() != null && replayRequest.getFilter().getKinds() != null && !replayRequest.getFilter().getKinds().isEmpty()) {
            // If kinds are specified in the filter, use them immediately
            kinds = replayRequest.getFilter().getKinds();
            
            // Validate that the specified kinds have active records
            validateKindsHaveActiveRecords(kinds);
            
            // Create initial metadata records for these kinds
            createInitialMetadataRecords(replayId, kinds, replayRequest.getOperation());
            
            // Start the asynchronous replay process
            LOGGER.info("Starting asynchronous replay process for ID: " + replayId + " with specified kinds");
            parallelReplayProcessor.processReplayAsync(replayRequest, kinds);
        } else {
            // If no kinds specified, we need to get all kinds asynchronously
            LOGGER.info("No kinds specified, will determine kinds asynchronously for replay ID: " + replayId);
            
            // Capture the current request headers for use in the background thread
            final Map<String, String> requestHeaders = new HashMap<>(headers.getHeaders());
            
            // Start an async task to get all kinds and then process them
            executorService.submit(() -> {
                // Execute within the request scope using the captured headers
                requestScopeUtil.executeInRequestScope(() -> {
                    try {
                        // Get all kinds - this is the expensive operation
                        Map<String, Long> kindCounts = queryRepository.getActiveRecordsCount();
                        List<String> allKinds = new ArrayList<>(kindCounts.keySet());
                        
                        LOGGER.info("Found " + allKinds.size() + " kinds for replay ID: " + replayId);
                        
                        // Create metadata records for all kinds
                        createInitialMetadataRecords(replayId, allKinds, replayRequest.getOperation());
                        
                        // Now start the actual replay processing
                        parallelReplayProcessor.processReplayAsync(replayRequest, allKinds);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error getting kinds for replay ID: " + replayId, e);
                        // Log the failure but don't throw - the API has already returned
                        auditLogger.createReplayRequestFail(Collections.singletonList("Error getting kinds: " + e.getMessage()));
                    }
                }, requestHeaders);
            });
        }
        
        // Log success
        auditLogger.createReplayRequestSuccess(Collections.singletonList("Replay started for ID: " + replayId));
        
        // Return immediately with the replay ID
        return new ReplayResponse(replayId);
    }
    
    /**
     * Validates that the specified kinds have active records.
     * Throws an AppException with a 400 status code if any kind has no active records.
     * Uses the more efficient getActiveRecordsCountForKinds method to check only the specified kinds.
     * 
     * @param kinds The list of kinds to validate
     */
    private void validateKindsHaveActiveRecords(List<String> kinds) {
        LOGGER.info("Validating that kinds have active records: " + kinds);

        Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(kinds);
        
        List<String> kindsWithNoRecords = new ArrayList<>();
        for (String kind : kinds) {
            if (!kindCounts.containsKey(kind) || kindCounts.get(kind) <= 0) {
                kindsWithNoRecords.add(kind);
            }
        }
        
        if (!kindsWithNoRecords.isEmpty()) {
            LOGGER.warning("The following kinds have no active records: " + String.join(", ", kindsWithNoRecords));
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid kind", "The requested kind does not exist.");
        }
    }
    
    /**
     * Creates initial metadata records for the specified kinds.
     * 
     * @param replayId The replay ID
     * @param kinds The list of kinds
     * @param operation The operation
     */
    private void createInitialMetadataRecords(String replayId, List<String> kinds, String operation) {
        LOGGER.info("Creating initial metadata records for " + kinds.size() + " kinds for replay ID: " + replayId);
        
        for (String kind : kinds) {
            try {
                ReplayMetaDataDTO replayMetaData = new ReplayMetaDataDTO();
                replayMetaData.setId(kind);  // Use kind as the ID (hash key)
                replayMetaData.setReplayId(replayId);
                replayMetaData.setKind(kind);
                replayMetaData.setOperation(operation);
                replayMetaData.setState(ReplayState.QUEUED.name());
                replayMetaData.setStartedAt(new Date());
                
                // For initial creation, set counts to 0 - they'll be updated later
                replayMetaData.setTotalRecords(0L);
                replayMetaData.setProcessedRecords(0L);
                
                // Save the replay metadata for this kind
                replayRepository.save(replayMetaData);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error creating replay metadata for kind " + kind + ": " + e.getMessage(), e);
                // Continue with other kinds
            }
        }
    }
    
    /**
     * Creates an initial status record for a replay operation.
     * This ensures that users can query the status immediately after the replay ID is returned.
     *
     * @param replayId The replay ID
     * @param operation The operation type
     * @param filter The filter (may be null)
     */
    private void createInitialStatusRecord(String replayId, String operation, ReplayFilter filter) {
        try {
            LOGGER.info("Creating initial status record for replay ID: " + replayId);
            
            ReplayMetaDataDTO initialStatus = new ReplayMetaDataDTO();
            initialStatus.setId("system");  // Special ID for the initial record
            initialStatus.setReplayId(replayId);
            initialStatus.setKind("system");  // Special kind to indicate it's a system record
            initialStatus.setOperation(operation);
            initialStatus.setState(ReplayState.QUEUED.name());
            initialStatus.setStartedAt(new Date());
            initialStatus.setTotalRecords(0L);
            initialStatus.setProcessedRecords(0L);
            
            // Serialize the filter if present
            if (filter != null) {
                initialStatus.setFilter(filter);
            }
            
            // Save the initial status
            replayRepository.save(initialStatus);
        } catch (Exception e) {
            // Log but don't fail - this is just to improve user experience
            LOGGER.log(Level.WARNING, "Error creating initial status record: " + e.getMessage(), e);
        }
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
