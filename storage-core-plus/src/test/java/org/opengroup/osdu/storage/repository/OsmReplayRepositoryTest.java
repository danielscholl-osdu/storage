/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.service.Transaction;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.gcp.web.model.ReplayMetaData;
import org.opengroup.osdu.storage.provider.gcp.web.repository.OsmReplayRepository;

@ExtendWith(MockitoExtension.class)
class OsmReplayRepositoryTest {

  private static final String TENANT_1 = "opendes";
  private static final String REPLAY_ID = UUID.randomUUID().toString();
  private static final String KIND = "opendes:source:type:1.0.0";
  private static final String REPLAY_OPT = "replay";
  private static final String IN_PROGRESS_STATE = "IN_PROGRESS";

  @Mock private TenantInfo tenantInfo;

  @Mock private Context context;

  @Mock private Transaction transaction;

  @InjectMocks private OsmReplayRepository replayRepository;

  @BeforeEach
  public void setUp() {
    when(tenantInfo.getDataPartitionId()).thenReturn(TENANT_1);
    when(tenantInfo.getName()).thenReturn(TENANT_1);
  }

  @Test
  void shouldReturnReplayStatusByReplayId() {
    when(context.getResultsAsList(any())).thenReturn(List.of(buildReplayMetaData(REPLAY_ID, KIND)));
    List<ReplayMetaDataDTO> result = replayRepository.getReplayStatusByReplayId(REPLAY_ID);

    Assertions.assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(REPLAY_ID, result.get(0).getReplayId());
    assertEquals(KIND, result.get(0).getKind());
  }

  @Test
  void shouldReturnReplayStatusByKindAndReplayId() {
    when(context.getResultsAsList(any())).thenReturn(List.of(buildReplayMetaData(REPLAY_ID, KIND)));
    ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);

    Assertions.assertNotNull(result);
    assertEquals(REPLAY_ID, result.getReplayId());
    assertEquals(KIND, result.getKind());
  }

  @Test
  void shouldReturnNullReplayStatusByKindAndReplayId() {
    when(context.getResultsAsList(any())).thenReturn(new ArrayList<>());
    ReplayMetaDataDTO result = replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);

    Assertions.assertNull(result);
  }

  @Test
  void shouldReturnSavedReplayStatus() {
    when(context.beginTransaction(any())).thenReturn(transaction);
    ReplayMetaDataDTO replayMetaData = new ReplayMetaDataDTO();
    replayMetaData.setReplayId(REPLAY_ID);
    replayMetaData.setKind(KIND);

    ReplayMetaDataDTO result = replayRepository.save(replayMetaData);

    assertEquals(REPLAY_ID, result.getReplayId());
  }

  private ReplayMetaData buildReplayMetaData(String replayId, String kind) {
    return ReplayMetaData.builder()
        .id(kind)
        .replayId(replayId)
        .kind(kind)
        .operation(REPLAY_OPT)
        .totalRecords(100L)
        .processedRecords(50L)
        .state(IN_PROGRESS_STATE)
        .startedAt(new Date())
        .elapsedTime(new Date().toString())
        .build();
  }
}
