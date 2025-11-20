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

package org.opengroup.osdu.storage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayData;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.enums.ReplayState;
import org.opengroup.osdu.storage.enums.ReplayType;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.opengroup.osdu.storage.request.ReplayRequest;

import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReplayServiceTest {

    private static final String KIND = "opendes:ds:inttest:1.0.4178321727827";
    @Mock
    private IReplayRepository replayRepository;

    @Mock
    private IQueryRepository queryRepository;

    @Mock
    private DpsHeaders headers;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private IMessageBus pubSubClient;

    @InjectMocks
    ReplayService replayService;


    @BeforeEach
    public void setup() {

        Map<String, Map<String, String>> resultMap = new HashMap<>();
        Map<String, String> reindexMap = new HashMap<>();
        reindexMap.put("topic", "reindextopic");
        reindexMap.put("queryBatchSize", "5000");
        reindexMap.put("publisherBatchSize", "50");


        Map<String, String> replayMap = new HashMap<>();
        replayMap.put("topic", "recordstopic");
        replayMap.put("queryBatchSize", "5000");
        replayMap.put("publisherBatchSize", "50");


        resultMap.put("reindex", reindexMap);
        resultMap.put("replay", replayMap);

        ReflectionTestUtils.setField(replayService, "replayOperationRoutingProperties", resultMap);

        lenient().when(headers.getCorrelationId()).thenReturn(UUID.randomUUID().toString());
        lenient().when(headers.getPartitionId()).thenReturn("dp1");
        lenient().when(headers.getCorrelationId()).thenReturn("dummy@osdu.com");

    }

    @Test
    public void testHandleReplayRequest_given_replayAll() {

        ReplayRequest replayRequest = new ReplayRequest();
        String replayId = UUID.randomUUID().toString();
        replayRequest.setOperation("reindex");
        replayRequest.setReplayId(replayId);
        HashMap<String, Long> recordCountByKind = new HashMap<>();
        Map<String, String> replayConfig = new HashMap<>();
        recordCountByKind.put("*", 10L);


        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder().replayId(UUID.randomUUID().toString()).totalRecords(10L)
                                                            .startedAt(new Date(System.currentTimeMillis()))
                                                            .operation(replayRequest.getOperation())
                                                            .filter(replayRequest.getFilter())
                                                            .build();

        when(queryRepository.getActiveRecordsCount()).thenReturn(recordCountByKind);
        when(replayRepository.save(any())).thenReturn(replayMetaData);
        ReplayResponse response = this.replayService.handleReplayRequest(replayRequest);
        verify(pubSubClient,times(1)).publishMessage(any(), any(),anyList());
        assertEquals(replayId,response.getReplayId());
    }

    @Test
    public void testHandleReplayRequest_given_replayByKind() {

        String replayId = UUID.randomUUID().toString();
        ReplayRequest replayRequest = new ReplayRequest();
        replayRequest.setOperation("reindex");
        ReplayFilter replayFilter = new ReplayFilter();
        replayFilter.setKinds(Arrays.asList(KIND));
        replayRequest.setFilter(replayFilter);
        replayRequest.setReplayId(replayId);

        HashMap<String, Long> recordCountByKind = new HashMap<>();
        Map<String, String> replayConfig = new HashMap<>();
        recordCountByKind.put(KIND, 10L);

        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder().replayId(replayRequest.getReplayId()).totalRecords(10L)
                                                            .startedAt(new Date(System.currentTimeMillis()))
                                                            .operation(replayRequest.getOperation())
                                                            .filter(replayRequest.getFilter())
                                                            .build();

        when(queryRepository.getActiveRecordsCountForKinds(replayRequest.getFilter().getKinds())).thenReturn(recordCountByKind);
        when(replayRepository.save(any())).thenReturn(replayMetaData);
        ReplayResponse response = this.replayService.handleReplayRequest(replayRequest);
        verify(pubSubClient,times(1)).publishMessage(any(), any(),anyList());
        assertEquals(replayId,response.getReplayId());
    }


    @Test
    public void testHandleReplayRequest_givenInvalidKind() {

        ReplayRequest replayRequest = new ReplayRequest();
        ReplayResponse expectedResponse = new ReplayResponse();
        replayRequest.setOperation("invalidOperation");
        try {
            ReplayResponse response = replayService.handleReplayRequest(replayRequest);
        }
        catch (AppException e) {
            assertEquals("Not a valid operation. The valid operation is: [reindex, replay]", e.getMessage());
        }
    }

    @Test
    public void testHandleReplayRequest_given_invalidKind() {


        ReplayRequest replayRequest = new ReplayRequest();
        ReplayResponse expectedResponse = new ReplayResponse();
        replayRequest.setOperation("reindex");
        ReplayFilter replayFilter = new ReplayFilter();
        replayFilter.setKinds(Arrays.asList(KIND));
        replayRequest.setFilter(replayFilter);
        HashMap<String, Long> recordCountByKind = new HashMap<>();
        Map<String, String> replayConfig = new HashMap<>();



        List<Object> replayMessages = new ArrayList<>();


        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder().replayId(replayRequest.getReplayId()).totalRecords(10L)
                                                            .startedAt(new Date(System.currentTimeMillis()))
                                                            .operation(replayRequest.getOperation())
                                                            .filter(replayRequest.getFilter())
                                                            .build();

        when(queryRepository.getActiveRecordsCountForKinds(replayRequest.getFilter().getKinds())).thenReturn(recordCountByKind);

        try {
            ReplayResponse response = replayService.handleReplayRequest(replayRequest);
        }
        catch (AppException e) {
            assertEquals("The requested kind does not exist.", e.getMessage());
        }
    }

    @Test
    public void test_processMessage_given_replayAll() {

        String id = "opendes:inttest:1.0.4178321727827";
        String replayId = UUID.randomUUID().toString();
        ReplayData body = ReplayData.builder()
                                    .id(UUID.randomUUID().toString())
                                    .replayType(ReplayType.REPLAY_ALL.name())
                                    .replayId(replayId)
                                    .startAtTimestamp(System.currentTimeMillis())
                                    .operation("reindex")
                                    .completionCount(0L)
                                    .totalCount(10L)
                                    .kind("*")
                                    .build();

        lenient().when(headers.getCorrelationId()).thenReturn(UUID.randomUUID().toString()+ "_kind_0_SEQ_0");
        ReplayMessage message = ReplayMessage.builder()
                                             .body(body)
                                             .headers(ReplayUtils.createHeaders(
                                                     headers.getPartitionId(),
                                                     ReplayUtils.getNextCorrelationId(headers.getCorrelationId(), Optional.empty()))).build();

        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder().
                                                            replayId(replayId)
                                                            .totalRecords(10L)
                                                            .processedRecords(5000L)
                                                            .startedAt(new Date(System.currentTimeMillis()))
                                                            .operation(message.getBody().getOperation())
                                                            .filter(null)
                                                            .build();

        RecordIdAndKind recordIdAndKind = new RecordIdAndKind();
        recordIdAndKind.setKind(KIND);
        recordIdAndKind.setId(id);
        RecordInfoQueryResult<RecordIdAndKind> recordInfoQueryResult = new RecordInfoQueryResult<>();
        recordInfoQueryResult.setResults(Arrays.asList(recordIdAndKind));
        recordInfoQueryResult.setCursor("dummyCursor");

        when(queryRepository.getAllRecordIdAndKind(any(),any())).thenReturn(recordInfoQueryResult);
        when(replayRepository.save(any())).thenReturn(replayMetaData);
        replayService.processReplayMessage(message);
        verify(replayRepository,times(1)).save(any());
        verify(queryRepository,times(1)).getAllRecordIdAndKind(5000,null);
    }

    @Test
    public void test_processMessage_given_replayByKind() {

        String id = "opendes:inttest:1.0.4178321727827";
        String replayId = UUID.randomUUID().toString();
        Map<String, String> replayConfig = new HashMap<>();
        ReplayData body = ReplayData.builder()
                                    .id(UUID.randomUUID().toString())
                                    .replayType(ReplayType.REPLAY_KIND.name())
                                    .replayId(replayId)
                                    .startAtTimestamp(System.currentTimeMillis())
                                    .operation("reindex")
                                    .completionCount(0L)
                                    .totalCount(10L)
                                    .kind(KIND)
                                    .build();

        lenient().when(headers.getCorrelationId()).thenReturn(UUID.randomUUID().toString()+ "_kind_0_SEQ_0");
        ReplayMessage message = ReplayMessage.builder()
                                             .body(body)
                                             .headers(ReplayUtils.createHeaders(
                                                     headers.getPartitionId(),
                                                     ReplayUtils.getNextCorrelationId(headers.getCorrelationId(),Optional.empty()))).build();

        ReplayMetaDataDTO replayMetaData = ReplayMetaDataDTO.builder().
                                                            replayId(replayId)
                                                            .totalRecords(10L)
                                                            .processedRecords(5000L)
                                                            .startedAt(new Date(System.currentTimeMillis()))
                                                            .operation(message.getBody().getOperation())
                                                            .filter(null)
                                                            .build();

        RecordId recordId = new RecordId();
        recordId.setId(id);
        RecordInfoQueryResult<RecordId> recordInfoQueryResult = new RecordInfoQueryResult<>();
        recordInfoQueryResult.setCursor("dummyCursor");
        recordInfoQueryResult.setResults(Arrays.asList(recordId));

        when(queryRepository.getAllRecordIdsFromKind(5000, message.getBody().getCursor(), message.getBody().getKind())).thenReturn(recordInfoQueryResult);

        when(replayRepository.save(any())).thenReturn(replayMetaData);


        this.replayService.processReplayMessage(message);
        verify(replayRepository,times(1)).save(any());
        verify(queryRepository,times(1)).getAllRecordIdsFromKind(5000,message.getBody().getCursor(), message.getBody().getKind());
    }

    @Test
    public void test_replayGetStatus_given_invalidReplayId() {

        String replayId = UUID.randomUUID().toString();
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(new ArrayList<ReplayMetaDataDTO>());
        try {
            ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        } catch (Exception e) {
            assertEquals("The replay ID "+ replayId + " is invalid.",e.getMessage());;
        }
    }

    @Test
    public void test_replayGetStatus_given_validReplayId() {

        List<String> kinds = Arrays.asList(KIND);
        String replayId = UUID.randomUUID().toString();
        ReplayMetaDataDTO metaDataDTO = new ReplayMetaDataDTO();
        metaDataDTO.setTotalRecords(10L);
        ReplayFilter replayFilter = new ReplayFilter();
        replayFilter.setKinds(kinds);
        metaDataDTO.setReplayId(replayId);
        metaDataDTO.setFilter(replayFilter);
        metaDataDTO.setOperation("reindex");

        ReplayMetaDataDTO statusDataDTO = new ReplayMetaDataDTO();
        statusDataDTO.setTotalRecords(10L);
        statusDataDTO.setKind(KIND);
        statusDataDTO.setProcessedRecords(10L);
        statusDataDTO.setStartedAt(new Date(System.currentTimeMillis()));
        statusDataDTO.setElapsedTime(ReplayUtils.formatMillisToHoursMinutesSeconds(System.currentTimeMillis()));
        statusDataDTO.setState(ReplayState.COMPLETED.name());

        List<ReplayMetaDataDTO> replayMetaDataDTOList = Arrays.asList(metaDataDTO,statusDataDTO);
        when(replayRepository.getReplayStatusByReplayId(replayId)).thenReturn(replayMetaDataDTOList);
        ReplayStatusResponse response = replayService.getReplayStatus(replayId);
        assertNotNull(response);
        assertNotNull(response.getFilter());
        assertEquals(response.getOverallState(),"COMPLETED");
    }
}
