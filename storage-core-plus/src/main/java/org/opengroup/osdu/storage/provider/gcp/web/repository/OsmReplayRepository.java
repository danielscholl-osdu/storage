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

package org.opengroup.osdu.storage.provider.gcp.web.repository;

import static org.opengroup.osdu.core.common.Constants.KIND;
import static org.opengroup.osdu.core.osm.core.model.where.condition.And.and;
import static org.opengroup.osdu.core.osm.core.model.where.predicate.Eq.eq;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.Kind;
import org.opengroup.osdu.core.osm.core.model.Namespace;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.service.Transaction;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.gcp.web.model.ReplayMetaData;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

@Repository
@Scope(SCOPE_SINGLETON)
@Slf4j
@RequiredArgsConstructor
public class OsmReplayRepository implements IReplayRepository {

  private static final String REPLAY_ID = "replayId";
  private final Context context;
  private final TenantInfo tenantInfo;

  public static final Kind REPLAY_STATUS_KIND = new Kind("ReplayStatus");

  @Override
  public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {
    GetQuery<ReplayMetaData> query =
        new GetQuery<>(ReplayMetaData.class, getDestination(), eq(REPLAY_ID, replayId));

    return Optional.ofNullable(context.getResultsAsList(query))
        .orElseGet(Collections::emptyList)
        .stream()
        .map(this::toReplayMetaDataDTO)
        .toList();
  }

  @Override
  public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {
    GetQuery<ReplayMetaData> query =
        new GetQuery<>(
            ReplayMetaData.class, getDestination(), and(eq(KIND, kind), eq(REPLAY_ID, replayId)));
    return Optional.ofNullable(context.getResultsAsList(query))
        .orElseGet(Collections::emptyList)
        .stream()
        .findFirst()
        .map(this::toReplayMetaDataDTO)
        .orElse(null);
  }

  @Override
  public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaDataDTO) {
    Transaction txn = context.beginTransaction(getDestination());
    try {
      context.upsert(getDestination(), toReplayMetaData(replayMetaDataDTO));
      txn.commitIfActive();
    } catch (Exception e) {
      if (txn != null) {
        txn.rollbackIfActive();
      }
      throw e;
    }
    return replayMetaDataDTO;
  }

  private Destination getDestination() {
    return Destination.builder()
        .partitionId(tenantInfo.getDataPartitionId())
        .namespace(new Namespace(tenantInfo.getName()))
        .kind(REPLAY_STATUS_KIND)
        .build();
  }

  private ReplayMetaDataDTO toReplayMetaDataDTO(ReplayMetaData replayMetaData) {
    if (replayMetaData == null) {
      return null;
    }
    return ReplayMetaDataDTO.builder()
        .id(replayMetaData.getId())
        .replayId(replayMetaData.getReplayId())
        .kind(replayMetaData.getKind())
        .operation(replayMetaData.getOperation())
        .totalRecords(replayMetaData.getTotalRecords())
        .startedAt(replayMetaData.getStartedAt())
        .filter(replayMetaData.getFilter())
        .processedRecords(replayMetaData.getProcessedRecords())
        .state(replayMetaData.getState())
        .elapsedTime(replayMetaData.getElapsedTime())
        .build();
  }

  private ReplayMetaData toReplayMetaData(ReplayMetaDataDTO replayMetaDataDTO) {
    if (replayMetaDataDTO == null) {
      return null;
    }
    return ReplayMetaData.builder()
        .id(replayMetaDataDTO.getId())
        .replayId(replayMetaDataDTO.getReplayId())
        .kind(replayMetaDataDTO.getKind())
        .operation(replayMetaDataDTO.getOperation())
        .totalRecords(replayMetaDataDTO.getTotalRecords())
        .startedAt(replayMetaDataDTO.getStartedAt())
        .filter(replayMetaDataDTO.getFilter())
        .processedRecords(replayMetaDataDTO.getProcessedRecords())
        .state(replayMetaDataDTO.getState())
        .elapsedTime(replayMetaDataDTO.getElapsedTime())
        .build();
  }
}
