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
import static org.opengroup.osdu.core.osm.core.model.where.predicate.Ge.ge;
import static org.opengroup.osdu.core.osm.core.model.where.predicate.In.in;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import com.github.fge.jsonpatch.JsonPatch;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.search.SortOrder;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.Kind;
import org.opengroup.osdu.core.osm.core.model.Namespace;
import org.opengroup.osdu.core.osm.core.model.order.OrderBy;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.model.where.Where;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.translate.Outcome;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

@Repository
@Scope(SCOPE_SINGLETON)
@Slf4j
@RequiredArgsConstructor
public class OsmRecordsMetadataRepository implements IRecordsMetadataRepository<String> {

  private final Context context;
  private final TenantInfo tenantInfo;

  public static final Kind RECORD_KIND = new Kind("StorageRecord");
  public static final Kind SCHEMA_KIND = new Kind(ISchemaRepository.SCHEMA_KIND);

  public static final String KIND = "kind";
  public static final String ID = "id";
  public static final String MODIFY_TIME = "modifyTime";
  public static final String CREATE_TIME = "createTime";
  public static final String LEGAL_TAGS = "legal.legaltags";
  public static final String LEGAL_COMPLIANCE = "legal.status";
  public static final String STATUS = "status";

  @Override
  public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata,
      Optional<CollaborationContext> collaborationContext) {
    if (recordsMetadata != null) {
      List<String> ids = recordsMetadata.stream().map(RecordMetadata::getId).toList();
      for (String id : ids) {
        validateOriginalId(id, collaborationContext);
      }
      List<RecordMetadata> recordsToSave = new ArrayList<>();
      for (RecordMetadata record : recordsMetadata) {
        if (collaborationContext.isPresent()) {
          RecordMetadata cloned = shallowCloneRecord(record);
          String namespacedId = CollaborationContextUtil.composeIdWithNamespace(record.getId(), collaborationContext);
          cloned.setId(namespacedId);
          recordsToSave.add(cloned);
        } else {
          recordsToSave.add(record);
        }
      }
      RecordMetadata[] metadata = recordsToSave.toArray(RecordMetadata[]::new);
      context.upsert(getDestination(), metadata);
    }
    return recordsMetadata;
  }

  private RecordMetadata shallowCloneRecord(RecordMetadata original) {
    RecordMetadata copy = new RecordMetadata();
    copy.setId(original.getId());
    copy.setKind(original.getKind());
    copy.setAcl(original.getAcl());
    copy.setLegal(original.getLegal());
    copy.setAncestry(original.getAncestry());
    copy.setTags(original.getTags());
    copy.setStatus(original.getStatus());
    copy.setUser(original.getUser());
    copy.setCreateTime(original.getCreateTime());
    copy.setModifyUser(original.getModifyUser());
    copy.setModifyTime(original.getModifyTime());
    copy.setGcsVersionPaths(original.getGcsVersionPaths());
    copy.setHash(original.getHash());
    return copy;
  }

  @Override
  public void delete(String id, Optional<CollaborationContext> collaborationContext) {
    validateOriginalId(id, collaborationContext);
    String composedId = CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext);
    context.deleteById(RecordMetadata.class, getDestination(), composedId);
  }

  @Override
  public void batchDelete(List<String> ids, Optional<CollaborationContext> collaborationContext) {
    if (CollectionUtils.isEmpty(ids)) {
      return;
    }
    for (String id : ids) {
      validateOriginalId(id, collaborationContext);
    }

    List<String> prefixedIds = ids.stream()
        .map(id -> CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext))
        .toList();
    String id = prefixedIds.get(0);
    List<String> subList = prefixedIds.subList(1, prefixedIds.size());
    context.deleteById(RecordMetadata.class, getDestination(), id, subList.toArray(new String[0]));
  }

  @Override
  public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
    validateOriginalId(id, collaborationContext);
    String lookupId = CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext);
    GetQuery<RecordMetadata> osmQuery = new GetQuery<>(RecordMetadata.class, getDestination(), eq("id", lookupId));
    return context.getResultsAsList(osmQuery).stream()
        .filter(Objects::nonNull)
        .findFirst()
        .map(record -> restoreOriginalId(record, collaborationContext))
        .orElse(null);
  }

  @Override
  public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status,
      int limit) {

    GetQuery<RecordMetadata>.GetQueryBuilder<RecordMetadata> builder = new GetQuery<>(RecordMetadata.class,
        getDestination()).toBuilder();
    if (status == null) {
      builder.where(eq(LEGAL_TAGS, legalTagName));
    } else {
      builder.where(and(eq(LEGAL_TAGS, legalTagName), eq(LEGAL_COMPLIANCE, status.name())));
    }

    Outcome<RecordMetadata> out = context.getResults(builder.build(), null, limit, null).outcome();
    return new AbstractMap.SimpleEntry<>(out.getPointer(), out.getList());
  }

  @Override
  public RecordInfoQueryResult<RecordMetadata> getRecords(String kind, Long modifiedAfterTime, String cursor, int limit,
      boolean deletedRecords, SortOrder sortOrder, Optional<CollaborationContext> collaborationContext) {
    Where whereClause = eq(STATUS, deletedRecords ? RecordState.deleted.name() : RecordState.active.name());
    String sortField = CREATE_TIME;

    if (StringUtils.isNotEmpty(kind)) {
      whereClause = and(whereClause, eq(KIND, kind));
    }

    if (modifiedAfterTime != null) {
      whereClause = and(whereClause, ge(MODIFY_TIME, modifiedAfterTime));
      sortField = MODIFY_TIME;
    }

    OrderBy orderByClause =
        (sortOrder == null || sortOrder == SortOrder.ASC) ? OrderBy.builder().addAsc(sortField).build() :
            OrderBy.builder().addDesc(sortField).build();

    GetQuery<RecordMetadata> query = new GetQuery<>(RecordMetadata.class, getDestination(),
        whereClause, orderByClause);

    Outcome<RecordMetadata> queryOutcome = context.getResults(query, null, limit, cursor)
        .outcome();

    List<RecordMetadata> resultList = queryOutcome.getList() != null ? queryOutcome.getList() : new ArrayList<>();

    // Filter by collaboration context and restore original IDs
    if (collaborationContext.isPresent()) {
      String namespace = CollaborationContextUtil.getNamespace(collaborationContext);
      resultList = resultList.stream()
          .filter(record -> record != null && record.getId() != null && record.getId().startsWith(namespace))
          .peek(record -> restoreOriginalId(record, collaborationContext))
          .collect(Collectors.toList());
    } else {
      // SOR only: filter out records that have any collaboration prefix (contain ":" before the normal ID format)
      resultList = resultList.stream()
          .filter(record -> record != null && record.getId() != null && !hasCollaborationPrefix(record.getId()))
          .collect(Collectors.toList());
    }

    String nextCursor = CollectionUtils.isEmpty(resultList) ? null : queryOutcome.getPointer();
    return new RecordInfoQueryResult<>(nextCursor, resultList);
  }

  @Override
  public Map<String, RecordMetadata> get(List<String> ids,
      Optional<CollaborationContext> collaborationContext) {
    if (ids.isEmpty()) {
      return new HashMap<>();
    }
    for (String id : ids) {
      validateOriginalId(id, collaborationContext);
    }

    List<String> lookupIds = ids.stream()
        .map(id -> CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext))
        .collect(Collectors.toList());
    GetQuery<RecordMetadata> recordsMetadataInQuery = new GetQuery<>(
        RecordMetadata.class,
        getDestination(),
        in("id", lookupIds)
    );
    List<RecordMetadata> resultsAsList = context.getResultsAsList(recordsMetadataInQuery);

    // Return map with composed IDs as keys, RecordMetadata objects keep original IDs restored
    Map<String, RecordMetadata> result = new HashMap<>();
    for (RecordMetadata record : resultsAsList) {
      if (record == null) {
        continue;
      }
      String composedId = record.getId();
      restoreOriginalId(record, collaborationContext);
      result.put(composedId, record);
    }
    return result;
  }

  //TODO remove when other providers replace with new method queryByLegal
  @Override
  public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit,
      String cursor) {
    return queryByLegal(legalTagName, null, limit);
  }

  @Override
  public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName[], int limit,
      String cursor) {
    throw new UnsupportedOperationException("Method not implemented.");
  }

  @Override
  public Map<String, String> patch(
      Map<RecordMetadata, JsonPatch> jsonPatchPerRecord,
      Optional<CollaborationContext> collaborationContext) {
    if (Objects.nonNull(jsonPatchPerRecord)) {
      RecordMetadata[] newRecordMetadata = new RecordMetadata[jsonPatchPerRecord.size()];
      int count = 0;
      for (Entry<RecordMetadata, JsonPatch> entry : jsonPatchPerRecord.entrySet()) {
        JsonPatch jsonPatch = entry.getValue();
        RecordMetadata recordToPatch = entry.getKey();
        RecordMetadata patched = JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, recordToPatch);
        if (collaborationContext.isPresent()) {
          // Use safe composition to avoid double-prefixing if ID already has collaboration namespace
          String safeId = CollaborationContextUtil.composeIdWithNamespace(patched.getId(),
              collaborationContext);
          patched.setId(safeId);
        }
        newRecordMetadata[count] = patched;
        count++;
      }
      context.upsert(getDestination(), newRecordMetadata);
    }
    return new HashMap<>();
  }

  private Destination getDestination() {
    return Destination.builder().partitionId(tenantInfo.getDataPartitionId())
        .namespace(new Namespace(tenantInfo.getName())).kind(RECORD_KIND).build();
  }

  private RecordMetadata restoreOriginalId(RecordMetadata record, Optional<CollaborationContext> collaborationContext) {
    if (record != null && collaborationContext.isPresent()) {
      record.setId(CollaborationContextUtil.getIdWithoutNamespace(record.getId(), collaborationContext));
    }
    return record;
  }

  /**
   * Checks if ID has a collaboration context UUID prefix.
   */
  private boolean hasCollaborationPrefix(String id) {
    if (id == null || id.length() < 36) {
      return false;
    }
    try {
      UUID.fromString(id.substring(0, 36));
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Validates single ID - must be original format when collaboration context present.
   */
  private void validateOriginalId(String id, Optional<CollaborationContext> collaborationContext) {
    if (StringUtils.isBlank(id)) {
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Invalid ID", "Record ID cannot be null or empty");
    }
    if (collaborationContext.isPresent() && hasCollaborationPrefix(id)) {
      log.warn("ID '{}' already has collaboration prefix - expected original format", id);
      throw new AppException(HttpStatus.BAD_REQUEST.value(), "Invalid ID",
          "ID already has collaboration prefix: " + id);
    }
  }
}
