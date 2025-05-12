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

import com.github.fge.jsonpatch.JsonPatch;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.Kind;
import org.opengroup.osdu.core.osm.core.model.Namespace;
import org.opengroup.osdu.core.osm.core.model.query.GetQuery;
import org.opengroup.osdu.core.osm.core.service.Context;
import org.opengroup.osdu.core.osm.core.translate.Outcome;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.util.JsonPatchUtil;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;


import static org.opengroup.osdu.core.osm.core.model.where.condition.And.and;
import static org.opengroup.osdu.core.osm.core.model.where.predicate.Eq.eq;
import static org.opengroup.osdu.core.osm.core.model.where.predicate.In.in;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Repository
@Scope(SCOPE_SINGLETON)
@Log
@RequiredArgsConstructor
public class OsmRecordsMetadataRepository implements IRecordsMetadataRepository<String> {

    private final Context context;
    private final TenantInfo tenantInfo;

    public static final Kind RECORD_KIND = new Kind("StorageRecord");
    public static final Kind SCHEMA_KIND = new Kind(ISchemaRepository.SCHEMA_KIND);

    public static final String KIND = "kind";
    public static final String ID = "id";
    public static final String LEGAL_TAGS = "legal.legaltags";
    public static final String LEGAL_COMPLIANCE = "legal.status";
    public static final String STATUS = "status";

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata,
        Optional<CollaborationContext> collaborationContext) {
      if (recordsMetadata != null) {
        RecordMetadata[] metadata = recordsMetadata.toArray(RecordMetadata[]::new);
        context.upsert(getDestination(), metadata);
      }
      return recordsMetadata;
    }

    @Override
    public void delete(String id, Optional<CollaborationContext> collaborationContext) {
        context.deleteById(RecordMetadata.class, getDestination(), id);
    }

  @Override
  public void batchDelete(List<String> ids, Optional<CollaborationContext> collaborationContext) {
    String id = ids.get(0);
    //TODO update the OSM API to pass just a list of IDs instead of 1 ID and varargs
    List<String> subList = ids.subList(1, ids.size());
    context.deleteById(RecordMetadata.class, getDestination(), id, subList.toArray(new String[0]));
  }

  @Override
    public RecordMetadata get(String id, Optional<CollaborationContext> collaborationContext) {
        GetQuery<RecordMetadata> osmQuery = new GetQuery<>(RecordMetadata.class, getDestination(), eq("id", id));
        return context.getResultsAsList(osmQuery).stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName, LegalCompliance status, int limit) {

        GetQuery<RecordMetadata>.GetQueryBuilder<RecordMetadata> builder = new GetQuery<>(RecordMetadata.class, getDestination()).toBuilder();
        if (status == null) {
            builder.where(eq(LEGAL_TAGS, legalTagName));
        } else {
            builder.where(and(eq(LEGAL_TAGS, legalTagName), eq(LEGAL_COMPLIANCE, status.name())));
        }

        Outcome<RecordMetadata> out = context.getResults(builder.build(), null, limit, null).outcome();
        return new AbstractMap.SimpleEntry<>(out.getPointer(), out.getList());
    }

    @Override
    public Map<String, RecordMetadata> get(List<String> ids,
        Optional<CollaborationContext> collaborationContext) {
        GetQuery<RecordMetadata> recordsMetadataInQuery = new GetQuery<>(
            RecordMetadata.class,
            getDestination(),
            in("id", ids)
        );
        return context.getResultsAsList(recordsMetadataInQuery).stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(RecordMetadata::getId, recordMetadata -> recordMetadata));
    }

    //TODO remove when other providers replace with new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, String cursor) {
        return queryByLegal(legalTagName, null, limit);
    }

    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName[], int limit, String cursor) {
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
        newRecordMetadata[count] = JsonPatchUtil.applyPatch(jsonPatch, RecordMetadata.class, entry.getKey());
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
}
