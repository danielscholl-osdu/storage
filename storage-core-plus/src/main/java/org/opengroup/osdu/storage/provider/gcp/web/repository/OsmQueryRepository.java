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

import static org.opengroup.osdu.core.osm.core.model.where.condition.And.and;
import static org.opengroup.osdu.core.osm.core.model.where.predicate.Eq.eq;
import static org.opengroup.osdu.storage.provider.gcp.web.repository.OsmRecordsMetadataRepository.*;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.Namespace;
import org.opengroup.osdu.core.osm.core.model.aggregation.Count;
import org.opengroup.osdu.core.osm.core.model.order.OrderBy;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.translate.Outcome;
import org.opengroup.osdu.core.osm.core.translate.ViewResult;
import org.opengroup.osdu.storage.model.RecordId;
import org.opengroup.osdu.storage.model.RecordIdAndKind;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

@Repository
@Scope(SCOPE_SINGLETON)
@Slf4j
@RequiredArgsConstructor
public class OsmQueryRepository implements IQueryRepository {

  private final Context context;
  private final TenantInfo tenantInfo;

  @Override
  public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
    GetQuery<RecordMetadata> q =
        new GetQuery<>(
            RecordMetadata.class,
            getDestination(),
            eq(STATUS, RecordState.active),
            OrderBy.builder().addAsc(KIND).build());

    Outcome<ViewResult> out =
        context
            .getViewResults(
                q, null, getLimitTuned(limit), Collections.singletonList(KIND), true, cursor)
            .outcome();

    List<String> kinds = out.getList().stream().map(e -> (String) e.get(KIND)).toList();
    return new DatastoreQueryResult(out.getPointer(), kinds);
  }

  @Override
  public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {
    GetQuery<RecordMetadata> q =
        new GetQuery<>(
            RecordMetadata.class,
            getDestination(),
            and(eq(KIND, kind), eq(STATUS, RecordState.active)));

    Outcome<ViewResult> out =
        context
            .getViewResults(
                q, null, getLimitTuned(limit), Collections.singletonList(ID), false, cursor)
            .outcome();
    List<String> result = out.getList().stream().map(e -> (String) e.get(ID)).collect(Collectors.toList());

    if (collaborationContext.isPresent()) {
        String namespace = CollaborationContextUtil.getNamespace(collaborationContext);
        result = result.stream()
            .filter(id -> id.startsWith(namespace))
            .map(id -> CollaborationContextUtil.getIdWithoutNamespace(id, collaborationContext))
            .collect(Collectors.toList());
    } else {
        result = result.stream()
            .filter(id -> !hasCollaborationPrefix(id))
            .collect(Collectors.toList());
    }

    String nextCursor = out.getPointer();
    if(result.isEmpty()){
      nextCursor = null;
    }
    return new DatastoreQueryResult(nextCursor, result);
  }

  @Override
  public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
    GetQuery<RecordMetadata> q =
        new GetQuery<>(RecordMetadata.class, getDestination(), eq(STATUS, RecordState.active));

    Outcome<ViewResult> outcome =
        context
            .getViewResults(
                q, null, getLimitTuned(limit), Collections.singletonList(ID), false, cursor)
            .outcome();

    List<RecordIdAndKind> records =
        outcome.getList().stream()
            .map(
                viewRecord -> {
                  String id = (String) viewRecord.get(ID);
                  String kind = (String) viewRecord.get(KIND);
                  RecordIdAndKind result = new RecordIdAndKind();
                  result.setId(id);
                  result.setKind(kind);
                  return result;
                })
            .toList();
    String nextCursor = outcome.getPointer();
    if(records.isEmpty()){
      nextCursor = null;
    }
    return new RecordInfoQueryResult<>(nextCursor, records);
  }

  @Override
  public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
    GetQuery<RecordMetadata> q =
        new GetQuery<>(
            RecordMetadata.class,
            getDestination(),
            and(eq(KIND, kind), eq(STATUS, RecordState.active)));

    Outcome<ViewResult> outcome =
        context
            .getViewResults(
                q, null, getLimitTuned(limit), Collections.singletonList(ID), false, cursor)
            .outcome();

    List<RecordId> records =
        outcome.getList().stream()
            .map(
                viewRecord -> {
                  String id = (String) viewRecord.get(ID);
                  RecordId result = new RecordId();
                  result.setId(id);
                  return result;
                })
            .toList();
    String nextCursor = outcome.getPointer();
    if(records.isEmpty()){
      nextCursor = null;
    }
    return new RecordInfoQueryResult<>(nextCursor, records);
  }

  @Override
  public HashMap<String, Long> getActiveRecordsCount() {
    HashMap<String, Long> kindCounts = new HashMap<>();
    try {
      List<String> kinds = getAllKinds(null, null).getResults();
      for (String kind : kinds) {
        Long count = getActiveRecordCountForKind(kind);
        if (count != null && count > 0 ) {
          kindCounts.put(kind, count);
        }
      }
      return kindCounts;
    } catch (Exception e) {
      throw new AppException(
          HttpStatus.SC_INTERNAL_SERVER_ERROR,
          "Error retrieving active records count",
          e.getMessage(),
          e);
    }
  }

  @Override
  public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
    Map<String, Long> kindCounts = new HashMap<>();
    for (String kind : kinds) {
      try {
        long count = getActiveRecordCountForKind(kind);
        if (count != 0) {
          kindCounts.put(kind, count);
        }
      } catch (Exception e) {
        log.error("Error counting records for kind = %s : %s".formatted(kind, e.getMessage()), e);
      }
    }
    return kindCounts;
  }

  public Long getActiveRecordCountForKind(String kind) {
    GetQuery<Long> q =
        new GetQuery<>(
            Long.class,
            getDestination(),
            and(eq(KIND, kind), eq(STATUS, RecordState.active)), Count.countAll());

    List<Long> resultsAsList = context.getResultsAsList(q);

    return resultsAsList.get(0);
  }

  private int getLimitTuned(Integer limit) {
    return (limit != null && limit > 0) ? limit : PAGE_SIZE;
  }

  protected Destination getDestination() {
    return Destination.builder()
        .partitionId(tenantInfo.getDataPartitionId())
        .namespace(new Namespace(tenantInfo.getName()))
        .kind(RECORD_KIND)
        .build();
  }

  /**
   * Checks if ID has a collaboration context UUID prefix (UUID:originalId format).
   */
  private boolean hasCollaborationPrefix(String id) {
    if (id == null || id.length() < 36) {
      return false;
    }
    try {
      java.util.UUID.fromString(id.substring(0, 36));
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
