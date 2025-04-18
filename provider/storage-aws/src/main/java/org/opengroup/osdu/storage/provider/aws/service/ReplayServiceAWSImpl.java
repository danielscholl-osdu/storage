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

package org.opengroup.osdu.storage.provider.aws.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.replay.ParallelReplayProcessor;
import org.opengroup.osdu.storage.provider.aws.replay.AwsReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.aws.replay.ReplayRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.QueryRepositoryImpl;
import org.opengroup.osdu.storage.provider.aws.util.RequestScopeUtil;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayStatus;
import org.opengroup.osdu.storage.enums.ReplayOperation;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    
    // Constants to avoid hardcoded strings
    private static final String SYSTEM_KIND = "system";
    private static final String ERROR_MSG_INVALID_KIND = "The requested kind does not exist.";
    private static final String ERROR_MSG_REPLAY_NOT_FOUND = "Replay not found";
    private static final String ERROR_MSG_VALIDATION_ERROR = "Validation Error";
    private static final String ERROR_MSG_INVALID_OPERATION = "Not a valid operation. The valid operations are: ";
    public static final String INVALID_REQUEST = "Invalid request";

    private final ReplayRepositoryImpl replayRepository;
    private final QueryRepositoryImpl queryRepository;
    private final DpsHeaders headers;
    private final StorageAuditLogger auditLogger;
    private final ParallelReplayProcessor parallelReplayProcessor;
    private final ExecutorService executorService;
    private final RequestScopeUtil requestScopeUtil;

    public ReplayServiceAWSImpl(
            ReplayRepositoryImpl replayRepository,
            QueryRepositoryImpl queryRepository,
            DpsHeaders headers, 
            StorageAuditLogger auditLogger,
            ParallelReplayProcessor parallelReplayProcessor,
            ExecutorService replayExecutorService,
            RequestScopeUtil requestScopeUtil) {
        this.replayRepository = replayRepository;
        this.queryRepository = queryRepository;
        this.headers = headers;
        this.auditLogger = auditLogger;
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
        if (replayId == null || replayId.isEmpty()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_REQUEST, "Replay ID cannot be null or empty");
        }
        
        List<AwsReplayMetaDataDTO> replayMetaDataList = replayRepository.getAwsReplayStatusByReplayId(replayId);
        
        if (replayMetaDataList == null || replayMetaDataList.isEmpty()) {
            throw new AppException(HttpStatus.SC_NOT_FOUND, ERROR_MSG_REPLAY_NOT_FOUND, 
                    "The replay ID " + replayId + " is invalid.");
        }
        
        // Check if we only have the initial system record
        boolean hasOnlySystemRecord = replayMetaDataList.size() == 1 && 
                                     SYSTEM_KIND.equals(replayMetaDataList.get(0).getKind());
        
        // Calculate overall status
        ReplayState overallState = calculateOverallState(replayMetaDataList);
        
        // For system-only records, we don't have real counts yet
        long totalRecords = hasOnlySystemRecord ? 0 : 
                replayMetaDataList.stream()
                    .filter(dto -> !SYSTEM_KIND.equals(dto.getKind()))
                    .mapToLong(ReplayMetaDataDTO::getTotalRecords)
                    .sum();
                    
        long processedRecords = hasOnlySystemRecord ? 0 : 
                replayMetaDataList.stream()
                    .filter(dto -> !SYSTEM_KIND.equals(dto.getKind()))
                    .mapToLong(ReplayMetaDataDTO::getProcessedRecords)
                    .sum();
        
        // Get the first record to extract common fields
        AwsReplayMetaDataDTO firstRecord = replayMetaDataList.get(0);
        
        // Calculate the start time (earliest start time across all records)
        Date startedAt = calculateEarliestStartTime(replayMetaDataList);
        
        // Calculate elapsed time based on completion status
        String elapsedTime = calculateElapsedTime(replayMetaDataList, overallState);
        
        // Create the response
        ReplayStatusResponse response = new ReplayStatusResponse();
        response.setReplayId(replayId);
        response.setOperation(firstRecord.getOperation());
        response.setOverallState(overallState.name());
        response.setStartedAt(startedAt);
        response.setElapsedTime(elapsedTime);
        response.setTotalRecords(totalRecords);
        response.setProcessedRecords(processedRecords);
        response.setFilter(firstRecord.getFilter());
        
        // Convert ReplayMetaDataDTO objects to ReplayStatus objects
        List<ReplayStatus> statusList = new ArrayList<>();
        for (AwsReplayMetaDataDTO dto : replayMetaDataList) {
            // Skip the system record in the detailed status list
            if (SYSTEM_KIND.equals(dto.getKind())) {
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
    
    private ReplayState calculateOverallState(List<? extends ReplayMetaDataDTO> replayMetaDataList) {
        // Filter out the system record for status calculation
        List<? extends ReplayMetaDataDTO> actualKindRecords = replayMetaDataList.stream()
                .filter(dto -> !SYSTEM_KIND.equals(dto.getKind()))
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
        validateReplayRequest(replayRequest);
        
        String replayId = ensureReplayId(replayRequest);
        
        // Create an initial status record immediately so users can query status right away
        createInitialStatusRecord(replayId, replayRequest.getOperation(), replayRequest.getFilter());
        
        // Process the replay request based on whether kinds are specified
        if (hasSpecifiedKinds(replayRequest)) {
            processReplayWithSpecifiedKinds(replayRequest, replayId);
        } else {
            processReplayWithAllKinds(replayRequest, replayId);
        }
        
        // Log success
        auditLogger.createReplayRequestSuccess(Collections.singletonList("Replay started for ID: " + replayId));
        
        // Return immediately with the replay ID
        return new ReplayResponse(replayId);
    }
    
    /**
     * Validates that the replay request is valid.
     * 
     * @param replayRequest The replay request to validate
     * @throws AppException if the request is invalid
     */
    private void validateReplayRequest(ReplayRequest replayRequest) {
        if (replayRequest == null) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_REQUEST, "Replay request cannot be null");
        }
        
        LOGGER.info("Handling replay request with operation: " + 
                    (replayRequest.getOperation() != null ? replayRequest.getOperation() : "null"));
        
        // Validate operation type
        Set<String> validReplayOperation = ReplayOperation.getValidReplayOperations();
        boolean isValidReplayOperation = replayRequest.getOperation() != null && 
                                        validReplayOperation.contains(replayRequest.getOperation());
        
        if (!isValidReplayOperation) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST,
                    ERROR_MSG_VALIDATION_ERROR, ERROR_MSG_INVALID_OPERATION + validReplayOperation);
        }
    }
    
    /**
     * Ensures that the replay request has a valid replay ID.
     * If no ID is provided, generates a new UUID.
     * 
     * @param replayRequest The replay request
     * @return The replay ID
     */
    private String ensureReplayId(ReplayRequest replayRequest) {
        if (replayRequest.getReplayId() == null || replayRequest.getReplayId().isEmpty()) {
            String replayId = UUID.randomUUID().toString();
            replayRequest.setReplayId(replayId);
            return replayId;
        }
        return replayRequest.getReplayId();
    }
    
    /**
     * Checks if the replay request has specified kinds in its filter.
     * 
     * @param replayRequest The replay request
     * @return true if kinds are specified, false otherwise
     */
    private boolean hasSpecifiedKinds(ReplayRequest replayRequest) {
        return replayRequest.getFilter() != null && 
               replayRequest.getFilter().getKinds() != null && 
               !replayRequest.getFilter().getKinds().isEmpty();
    }
    
    /**
     * Processes a replay request with specified kinds.
     * 
     * @param replayRequest The replay request
     * @param replayId The replay ID
     */
    private void processReplayWithSpecifiedKinds(ReplayRequest replayRequest, String replayId) {
        List<String> kinds = replayRequest.getFilter().getKinds();
        
        // Validate that the specified kinds have active records
        validateKindsHaveActiveRecords(kinds);
        
        // Create initial metadata records for these kinds
        createInitialMetadataRecords(replayId, kinds, replayRequest.getOperation());
        
        // Start the asynchronous replay process
        LOGGER.info(() -> String.format("Starting asynchronous replay process for ID: %s with specified kinds", replayId));
        parallelReplayProcessor.processReplayAsync(replayRequest, kinds);
    }
    
    /**
     * Processes a replay request with all available kinds.
     * This method runs asynchronously to avoid blocking the API response.
     * 
     * @param replayRequest The replay request
     * @param replayId The replay ID
     */
    private void processReplayWithAllKinds(ReplayRequest replayRequest, String replayId) {
        LOGGER.info(() -> String.format("No kinds specified, will determine kinds asynchronously for replay ID: %s", replayId));
        
        // Capture the current request headers for use in the background thread
        final Map<String, String> requestHeaders = captureRequestHeaders();
        
        // Start an async task to get all kinds and then process them
        executorService.submit(() -> requestScopeUtil.executeInRequestScope(
            () -> processAllKindsAsync(replayRequest, replayId), requestHeaders));
    }
    
    /**
     * Captures the current request headers for use in background threads.
     * 
     * @return A map of request headers
     */
    private Map<String, String> captureRequestHeaders() {
        final Map<String, String> requestHeaders = new HashMap<>();
        if (headers != null && headers.getHeaders() != null) {
            requestHeaders.putAll(headers.getHeaders());
        }
        return requestHeaders;
    }
    
    /**
     * Processes all kinds asynchronously.
     * This method is executed in a background thread.
     * 
     * @param replayRequest The replay request
     * @param replayId The replay ID
     */
    private void processAllKindsAsync(ReplayRequest replayRequest, String replayId) {
        try {
            // Get all kinds - this is the expensive operation
            Map<String, Long> kindCounts = queryRepository.getActiveRecordsCount();
            List<String> allKinds = new ArrayList<>(kindCounts.keySet());
            
            LOGGER.info(() -> String.format("Found %s kinds for replay ID: %s", allKinds.size(), replayId));
            
            // Create metadata records for all kinds
            createInitialMetadataRecords(replayId, allKinds, replayRequest.getOperation());
            
            // Now start the actual replay processing
            parallelReplayProcessor.processReplayAsync(replayRequest, allKinds);
        } catch (Exception e) {
            handleAsyncProcessingError(e, replayId);
        }
    }
    
    /**
     * Handles errors that occur during asynchronous processing.
     * 
     * @param e The exception that occurred
     * @param replayId The replay ID
     */
    private void handleAsyncProcessingError(Exception e, String replayId) {
        LOGGER.log(Level.SEVERE, String.format("Error getting kinds for replay ID: %s", replayId), e);
        // Log the failure but don't throw - the API has already returned
        auditLogger.createReplayRequestFail(Collections.singletonList("Error getting kinds: " + e.getMessage()));
        
        // Update the system record to indicate failure
        try {
            updateSystemRecordToFailed(replayId);
        } catch (Exception updateEx) {
            LOGGER.log(Level.SEVERE, "Failed to update system record status", updateEx);
        }
    }
    
    /**
     * Updates the system record to indicate a failure occurred.
     * 
     * @param replayId The replay ID
     */
    private void updateSystemRecordToFailed(String replayId) {
        try {
            List<ReplayMetaDataDTO> systemRecords = replayRepository.getReplayStatusByReplayId(replayId);
            if (systemRecords != null) {
                for (ReplayMetaDataDTO dataDTO : systemRecords) {
                    if (SYSTEM_KIND.equals(dataDTO.getKind())) {
                        // Convert to AWS DTO to preserve any AWS-specific fields
                        AwsReplayMetaDataDTO awsDto = AwsReplayMetaDataDTO.fromReplayMetaDataDTO(dataDTO);
                        awsDto.setState(ReplayState.FAILED.name());
                        awsDto.setLastUpdatedAt(new Date());
                        replayRepository.saveAwsReplayMetaData(awsDto);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating system record to failed state", e);
        }
    }
    
    /**
     * Validates that the specified kinds have active records.
     * Throws an AppException with a 400 status code if any kind has no active records.
     * Uses the more efficient getActiveRecordsCountForKinds method to check only the specified kinds.
     * 
     * @param kinds The list of kinds to validate
     */
    private void validateKindsHaveActiveRecords(List<String> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, INVALID_REQUEST, "Kinds list cannot be null or empty");
        }
        
        LOGGER.info(() -> String.format("Validating that kinds have active records: %s", kinds));

        Map<String, Long> kindCounts = queryRepository.getActiveRecordsCountForKinds(kinds);
        
        List<String> kindsWithNoRecords = new ArrayList<>();
        for (String kind : kinds) {
            if (!kindCounts.containsKey(kind) || kindCounts.get(kind) <= 0) {
                kindsWithNoRecords.add(kind);
            }
        }
        
        if (!kindsWithNoRecords.isEmpty()) {
            LOGGER.warning(() -> String.format("The following kinds have no active records: %s", String.join(", ", kindsWithNoRecords)));
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid kind", ERROR_MSG_INVALID_KIND);
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
        if (replayId == null || kinds == null || operation == null) {
            LOGGER.warning("Cannot create metadata records with null parameters");
            return;
        }
        
        LOGGER.info(() -> String.format("Creating initial metadata records for %s kinds for replay ID: %s", kinds.size(), replayId));
        
        for (String kind : kinds) {
            if (kind == null || kind.isEmpty()) {
                continue; // Skip null or empty kinds
            }
            
            try {
                AwsReplayMetaDataDTO replayMetaData = new AwsReplayMetaDataDTO();
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
                replayRepository.saveAwsReplayMetaData(replayMetaData);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Error creating replay metadata for kind %s: %s", kind, e.getMessage()), e);
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
     * @param filter The filter (can be null)
     */
    private void createInitialStatusRecord(String replayId, String operation, ReplayFilter filter) {
        if (replayId == null || operation == null) {
            LOGGER.warning("Cannot create initial status record with null replayId or operation");
            return;
        }
        
        try {
            LOGGER.info(() -> String.format("Creating initial status record for replay ID: %s", replayId));
            
            AwsReplayMetaDataDTO initialStatus = new AwsReplayMetaDataDTO();
            initialStatus.setId(SYSTEM_KIND);  // Special ID for the initial record
            initialStatus.setReplayId(replayId);
            initialStatus.setKind(SYSTEM_KIND);  // Special kind to indicate it's a system record
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
            replayRepository.saveAwsReplayMetaData(initialStatus);
        } catch (Exception e) {
            // Log but don't fail - this is just to improve user experience
            LOGGER.log(Level.WARNING, String.format("Error creating initial status record: %s", e.getMessage()), e);
        }
    }
    
    /**
     * Calculates the earliest start time across all replay records.
     * 
     * @param replayMetaDataList List of replay metadata records
     * @return The earliest start time
     */
    private Date calculateEarliestStartTime(List<? extends ReplayMetaDataDTO> replayMetaDataList) {
        Date earliestStartTime = null;
        
        for (ReplayMetaDataDTO dto : replayMetaDataList) {
            if (dto.getStartedAt() != null && (earliestStartTime == null || dto.getStartedAt().before(earliestStartTime))) {
                earliestStartTime = dto.getStartedAt();
            }
        }
        
        // If no valid start time found, use current time
        return earliestStartTime != null ? earliestStartTime : new Date();
    }
    
    /**
     * Calculates the elapsed time based on the completion status.
     * If the replay is completed, use the latest completion time.
     * If the replay is still in progress, calculate from current time.
     * 
     * @param replayMetaDataList List of replay metadata records
     * @param overallState The overall state of the replay
     * @return The elapsed time as a formatted string
     */
    String calculateElapsedTime(List<AwsReplayMetaDataDTO> replayMetaDataList, ReplayState overallState) {
        Date earliestStartTime = calculateEarliestStartTime(replayMetaDataList);
        long endTimeMillis;
        
        // For completed or failed replays, use the latest completion time
        if ((overallState == ReplayState.COMPLETED || overallState == ReplayState.FAILED)) {
            endTimeMillis = replayMetaDataList.stream()
                .filter(dto -> (ReplayState.COMPLETED.name().equals(dto.getState()) || 
                               ReplayState.FAILED.name().equals(dto.getState())) && 
                              dto.getLastUpdatedAt() != null)
                .map(dto -> dto.getLastUpdatedAt().getTime())
                .max(Long::compare)
                .orElse(System.currentTimeMillis()); // Fallback to current time if no completion time found
        } else {
            // For in-progress or queued replays, use current time
            endTimeMillis = System.currentTimeMillis();
        }
        
        return formatElapsedTime(endTimeMillis - earliestStartTime.getTime());
    }
    
    /**
     * Formats elapsed time in milliseconds to a human-readable string.
     * 
     * @param elapsedMillis Elapsed time in milliseconds
     * @return Formatted elapsed time string
     */
    private String formatElapsedTime(long elapsedMillis) {
        // Ensure elapsed time is non-negative
        elapsedMillis = Math.max(0, elapsedMillis);
        
        long seconds = elapsedMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
    
    /**
     * Processes a replay message.
     * This method delegates to the ReplayMessageProcessorAWSImpl through ReplayMessageHandler.
     *
     * @param replayMessage The replay message to process
     * @throws UnsupportedOperationException This method is not supported in AWS implementation
     */
    @Override
    public void processReplayMessage(ReplayMessage replayMessage) {
        // This method should not be called directly in AWS implementation
        // It's here to satisfy the interface requirements
        LOGGER.log(Level.SEVERE, "ReplayServiceAWSImpl.processReplayMessage called directly. This should be handled by ReplayMessageHandler instead.");
        throw new UnsupportedOperationException("This method should be called on ReplayMessageHandler");
    }
    
    /**
     * Handles a failure in processing a replay message.
     * This method delegates to the ReplayMessageProcessorAWSImpl through ReplayMessageHandler.
     *
     * @param replayMessage The replay message that failed
     * @throws UnsupportedOperationException This method is not supported in AWS implementation
     */
    @Override
    public void processFailure(ReplayMessage replayMessage) {
        // This method should not be called directly in AWS implementation
        // It's here to satisfy the interface requirements
        LOGGER.log(Level.SEVERE, "ReplayServiceAWSImpl.processFailure called directly. This should be handled by ReplayMessageHandler instead.");
        throw new UnsupportedOperationException("This method should be called on ReplayMessageHandler");
    }
}
