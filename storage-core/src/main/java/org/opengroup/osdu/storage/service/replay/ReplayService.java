// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.service.replay;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayOperation;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
@Service
public class ReplayService implements IReplayService {
    @Autowired
    private IReplayRepository replayRepository;

    @Autowired
    private IQueryRepository queryRepository;

    @Autowired
    private IMessageBus pubSubClient;

    private final static Logger logger = LoggerFactory.getLogger(ReplayService.class);

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Value("#{${replay.operation.routingProperties}}")
    private Map<String, Map<String, String>> replayOperationRoutingProperties;

    @Value("#{${replay.routingProperties}}")
    private Map<String, String> replayRoutingProperty;

    public ReplayResponse handleReplayRequest(ReplayRequest replayRequest) {

        Set<String> validReplayOperation = ReplayOperation.getValidReplayOperations();
        boolean isValidReplayOperation = validReplayOperation.contains(replayRequest.getOperation());

        logger.info("Replay request received: {}", replayRequest);

        if (!isValidReplayOperation)
            throw new AppException(HttpStatus.SC_BAD_REQUEST,
                    "Validation Error", "Not a valid operation. The valid operation is: " + validReplayOperation);

        if (!(ObjectUtils.isEmpty(replayRequest.getFilter()) || ObjectUtils.isEmpty(replayRequest.getFilter().getKinds())))
            return this.replay(replayRequest, ReplayType.REPLAY_KIND);

        return this.replay(replayRequest, ReplayType.REPLAY_ALL);
    }

    private ReplayResponse replay(ReplayRequest replayRequest, ReplayType replayOperation) {

        List<ReplayMessage> replayMessageList = this.generateReplayMessageList(replayRequest, replayOperation);
        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder()
                                                            .id(UUID.randomUUID().toString())
                                                            .replayId(replayRequest.getReplayId())
                                                            .totalRecords(getTotalRecordCount(replayMessageList))
                                                            .startedAt(ReplayUtils.formatMillisToDate(System.currentTimeMillis()))
                                                            .operation(replayRequest.getOperation())
                                                            .filter(replayRequest.getFilter())
                                                            .build();

        return this.startReplay(replayMessageList, replayMetaData);
    }

    private Long getTotalRecordCount(List<ReplayMessage> replayMessageList) {

        return replayMessageList.stream().mapToLong(item -> item.getBody().getTotalCount()).sum();
    }

    private List<ReplayMessage> generateReplayMessageList(ReplayRequest replayRequest, ReplayType replayType) throws AppException {

        Map<String, Long> countByKind = (replayType == ReplayType.REPLAY_ALL) ?
                queryRepository.getActiveRecordsCount() : queryRepository.getActiveRecordsCountForKinds(replayRequest.getFilter().getKinds());

        if (countByKind == null || countByKind.isEmpty())
            throw new AppException(HttpStatus.SC_BAD_REQUEST,
                    "Kind is invalid.", "The requested kind does not exist."
            );

        List<ReplayMessage> replayMessages = new ArrayList<>();
        int kindCounter = 0;

        for (Map.Entry<String, Long> item : countByKind.entrySet()) {
            long startedAtTimestamp = System.currentTimeMillis();

            ReplayData body = ReplayData.builder()
                                        .id(UUID.randomUUID().toString())
                                        .replayType(replayType.name())
                                        .replayId(replayRequest.getReplayId())
                                        .startAtTimestamp(startedAtTimestamp)
                                        .operation(replayRequest.getOperation())
                                        .completionCount(0L)
                                        .totalCount(item.getValue())
                                        .kind(item.getKey())
                                        .build();

            String messageCorrelationId = ReplayUtils.getNextCorrelationId(headers.getCorrelationId(), Optional.of(kindCounter));
            ReplayMessage message = ReplayMessage.builder()
                                                 .body(body)
                                                 .headers(ReplayUtils.createHeaders(
                                                         headers.getPartitionId(),
                                                         messageCorrelationId))
                                                 .build();

            logger.info("The replay message {} with new correlation ID {}", message, messageCorrelationId);
            replayMessages.add(message);
            kindCounter++;
        }

        return replayMessages;
    }

    private ReplayResponse startReplay(List<ReplayMessage> replayMessages, ReplayMetaDataDTO replayMetaData) {

        try {
            replayRepository.save(replayMetaData);
            pubSubClient.publishMessage(headers, replayRoutingProperty, replayMessages);
        } catch (Exception e) {

            logger.error("Exception occurred during start replay operation for replayId {} is ", replayMetaData.getReplayId(), e);
            auditLogger.createReplayRequestFail(Collections.singletonList(replayMetaData.toString()));
            throw new AppException(
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "The exception occurred during the start replay operation.",
                    "Request could not be processed due to an internal server issue."
            );
        }
        auditLogger.createReplayRequestSuccess(Collections.singletonList(replayMetaData.toString()));
        return ReplayResponse.builder().replayId(replayMetaData.getReplayId()).build();
    }

    public ReplayStatusResponse getReplayStatus(String replayId) {
        List<ReplayMetaDataDTO> replayMetaDataDTOList = replayRepository.getReplayStatusByReplayId(replayId);

        if (ObjectUtils.isEmpty(replayMetaDataDTOList))
            throw new AppException(
                    HttpStatus.SC_NOT_FOUND,
                    "Replay ID does not exist.",
                    "The replay ID " + replayId + " is invalid."
            );

        ReplayStatusResponse response = new ReplayStatusResponse();
        response.setReplayId(replayId);
        response.setStatus(new ArrayList<>());
        long totalProcessedRecords = 0;

        boolean allQueued = true;
        boolean hasFailed = false;
        boolean hasInProgress = false;

        for (ReplayMetaDataDTO replayMetaDataDTO : replayMetaDataDTOList) {
            if (replayMetaDataDTO.getKind() == null) {
                response.setOperation(replayMetaDataDTO.getOperation());
                response.setFilter(replayMetaDataDTO.getFilter());
                response.setTotalRecords(replayMetaDataDTO.getTotalRecords());
                response.setStartedAt(replayMetaDataDTO.getStartedAt());
            }
            else {
                Long processedRecords = replayMetaDataDTO.getProcessedRecords();
                totalProcessedRecords += processedRecords != null ? processedRecords : 0;
                response.setOverallState(replayMetaDataDTO.getState());
                response.getStatus().add(ReplayUtils.convertToReplayStatusDTO(replayMetaDataDTO));

                String state = replayMetaDataDTO.getState();
                if (state.equals(ReplayState.FAILED.name())) {
                    hasFailed = true;
                    allQueued = false;
                }
                else if (state.equals(ReplayState.IN_PROGRESS.name())) {
                    hasInProgress = true;
                    allQueued = false;
                }
                else if (!state.equals(ReplayState.QUEUED.name())) {
                    allQueued = false;
                }
            }
        }

        // Determine overall status based on the flags
        if (allQueued) {
            response.setOverallState(ReplayState.QUEUED.name());
        }
        else if (hasFailed) {
            response.setOverallState(ReplayState.FAILED.name());
        }
        else if (hasInProgress) {
            response.setOverallState(ReplayState.IN_PROGRESS.name());
        }
        else {
            response.setOverallState(ReplayState.COMPLETED.name());
        }
        response.setProcessedRecords(totalProcessedRecords);

        logger.info("GET Replay status operation successful. Replay status: {}", response);
        return response;
    }

    @Override
    public void processFailure(ReplayMessage replayMessage) {

        ReplayMetaDataDTO replayMetadata = replayRepository.getReplayStatusByKindAndReplayId(
                replayMessage.getBody().getKind(),
                replayMessage.getBody().getReplayId()
                                                                                            );
        replayMetadata.setState(ReplayState.FAILED.name());
        replayRepository.save(replayMetadata);
        try {
            auditLogger.createReplayRequestFail(Collections.singletonList(replayMetadata.toString()));
        } catch (Exception e) {
            logger.warn("Audit logger failed in processFailure: {}", e.getMessage(), e);
        }
        logger.error("Replay operation FAILED for replayId={}, kind={}, metadata={}", 
            replayMessage.getBody().getReplayId(), 
            replayMessage.getBody().getKind(), 
            replayMetadata);
    }

    public void processReplayMessage(ReplayMessage replayMessage) {

        String kind = replayMessage.getBody().getKind();
        String replayId = replayMessage.getBody().getReplayId();
        StopWatch stopWatch = new StopWatch();

        stopWatch.start("Fetch Record");
        RecordInfoQueryResult<RecordIdAndKind> recordInfoQueryResult = getRecordIdsAndKind(replayMessage);
        stopWatch.stop();

        logger.info("TaskName : {}, RecordIdsCount : {}, OperationTime : {} ms", stopWatch.getLastTaskName(),
                recordInfoQueryResult.getResults() == null ? 0 : recordInfoQueryResult.getResults().size(), stopWatch.getLastTaskTimeMillis());

        stopWatch.start("Published RecordChangedMessages");
        publishRecordChangedMessages(recordInfoQueryResult.getResults(), replayOperationRoutingProperties.get(replayMessage.getBody().getOperation()));
        stopWatch.stop();

        logger.info("TaskName : {}, RecordIdsCount : {}, OperationTime : {} ms", stopWatch.getLastTaskName(),
                recordInfoQueryResult.getResults().size(), stopWatch.getLastTaskTimeMillis());

        ReplayMetaDataDTO progress = saveProgress(replayMessage, recordInfoQueryResult.getResults().size(),
                recordInfoQueryResult.getCursor() == null ? ReplayState.COMPLETED : ReplayState.IN_PROGRESS
                                                 );

        logger.info("Replay operation progress tracked for ReplayId= {}, Kind= {}, Status= {}, ElapsedTime= {}, TotalRecord= {}",
                replayId, kind, progress.getState(), progress.getElapsedTime(), progress.getTotalRecords());

        if (recordInfoQueryResult.getCursor() == null)
            return;

        stopWatch.start("Publishing the Replay Message");

        String messageCorrelationId = ReplayUtils.getNextCorrelationId(headers.getCorrelationId(), Optional.empty());
        Map<String, String> newHeaders = ReplayUtils.createHeaders(headers.getPartitionId(), messageCorrelationId);

        ReplayData newData = ReplayData.builder().operation(replayMessage.getBody().getOperation())
                                       .id(replayMessage.getBody().getId())
                                       .kind(kind)
                                       .replayId(replayId)
                                       .replayType(replayMessage.getBody().getReplayType())
                                       .startAtTimestamp(replayMessage.getBody().getStartAtTimestamp())
                                       .completionCount(progress.getProcessedRecords())
                                       .totalCount(replayMessage.getBody().getTotalCount())
                                       .cursor(recordInfoQueryResult.getCursor())
                                       .build();

        ReplayMessage newMessage = ReplayMessage.builder().headers(newHeaders).body(newData).build();
        logger.info("The replay message {} with new correlation ID {}", newMessage, messageCorrelationId);
        List<ReplayMessage> newMessages = new ArrayList<>();
        newMessages.add(newMessage);
        this.pubSubClient.publishMessage(headers, replayRoutingProperty, newMessages);
        stopWatch.stop();

        logger.info("Processed ReplayMessage in {} ms", stopWatch.getTotalTimeMillis());
    }

    private RecordInfoQueryResult<RecordIdAndKind> getRecordIdsAndKind(ReplayMessage replayMessage) {

        RecordInfoQueryResult<RecordIdAndKind> recordInfoQueryResult = new RecordInfoQueryResult<>();
        Map<String, String> routingInfo = replayOperationRoutingProperties.get(replayMessage.getBody().getOperation());
        int BATCH_SIZE = Integer.parseInt(routingInfo.get("queryBatchSize"));

        if (replayMessage.getBody().getReplayType().equals(ReplayType.REPLAY_KIND.name())) {

            List<RecordIdAndKind> result = new ArrayList<>();
            RecordInfoQueryResult<RecordId> queryResult = queryRepository.getAllRecordIdsFromKind(
                    BATCH_SIZE,
                    replayMessage.getBody().getCursor(),
                    replayMessage.getBody().getKind()
                                                                                                 );

            for (RecordId recordId : queryResult.getResults()) {
                result.add(RecordIdAndKind.builder().id(recordId.getId()).kind(replayMessage.getBody().getKind()).build());
            }

            recordInfoQueryResult.setResults(result);
            recordInfoQueryResult.setCursor(queryResult.getCursor());

        }
        else if (replayMessage.getBody().getReplayType().equals(ReplayType.REPLAY_ALL.name())) {

            recordInfoQueryResult = queryRepository.getAllRecordIdAndKind(
                    BATCH_SIZE,
                    replayMessage.getBody().getCursor());
        }
        return recordInfoQueryResult;
    }

    private ReplayMetaDataDTO saveProgress(ReplayMessage replayMessage, int count, ReplayState replayState) {

        long totalElapsedInMillis = System.currentTimeMillis() - replayMessage.getBody().getStartAtTimestamp();

        String kind = replayMessage.getBody().getKind();
        String replayId = replayMessage.getBody().getReplayId();

        ReplayMetaDataDTO progress = ReplayMetaDataDTO.builder()
                                                      .id(replayMessage.getBody().getId())
                                                      .kind(kind)
                                                      .replayId(replayId)
                                                      .processedRecords(replayMessage.getBody().getCompletionCount() + count)
                                                      .totalRecords(replayMessage.getBody().getTotalCount())
                                                      .state(replayState.name())
                                                      .startedAt(new Date(replayMessage.getBody().getStartAtTimestamp()))
                                                      .elapsedTime(ReplayUtils.formatMillisToHoursMinutesSeconds(totalElapsedInMillis))
                                                      .build();

        logger.info("Replay Progress : {}", progress.toString());
        return replayRepository.save(progress);
    }

    private void publishRecordChangedMessages(List<RecordIdAndKind> recordQueryResult, Map<String, String> routingProperties) {

        PubSubInfo[] pubSubInfo = new PubSubInfo[recordQueryResult.size()];
        int index = 0;
        for (RecordIdAndKind recordIdAndKind : recordQueryResult) {
            if (recordIdAndKind != null && recordIdAndKind.getId() != null)
                pubSubInfo[index++] = PubSubInfo.builder().
                                                kind(recordIdAndKind.getKind())
                                                .id(recordIdAndKind.getId())
                                                .op(OperationType.create)
                                                .build();
        }
        pubSubClient.publishMessage(headers, routingProperties, pubSubInfo);
    }
}
