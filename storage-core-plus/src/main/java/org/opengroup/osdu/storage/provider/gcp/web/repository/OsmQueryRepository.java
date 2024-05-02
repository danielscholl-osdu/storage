/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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
import static org.opengroup.osdu.storage.provider.gcp.web.repository.OsmRecordsMetadataRepository.KIND;
import static org.opengroup.osdu.storage.provider.gcp.web.repository.OsmRecordsMetadataRepository.RECORD_KIND;
import static org.opengroup.osdu.storage.provider.gcp.web.repository.OsmRecordsMetadataRepository.STATUS;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import org.opengroup.osdu.core.osm.core.model.Destination;
import org.opengroup.osdu.core.osm.core.model.Namespace;
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
@Log
@RequiredArgsConstructor
public class OsmQueryRepository implements IQueryRepository {

    private final Context context;
    private final TenantInfo tenantInfo;

    @Override
    public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {

        GetQuery<RecordMetadata> q = new GetQuery<>(RecordMetadata.class, getDestination(),
            eq(STATUS, RecordState.active), OrderBy.builder().addAsc(KIND).build());
        Outcome<ViewResult> out = context.getViewResults(q, null, getLimitTuned(limit), Collections.singletonList(KIND), true, cursor).outcome();
        List<String> kinds = out.getList().stream().map(e -> (String) e.get(KIND)).collect(Collectors.toList());
        return new DatastoreQueryResult(out.getPointer(), kinds);
    }

    @Override
    public DatastoreQueryResult getAllRecordIdsFromKind(String kind, Integer limit, String cursor, Optional<CollaborationContext> collaborationContext) {

        GetQuery<RecordMetadata> q = new GetQuery<>(RecordMetadata.class, getDestination(), and(eq(KIND, kind), eq(STATUS, RecordState.active)));
        Outcome<ViewResult> out = context.getViewResults(q, null, getLimitTuned(limit), Collections.singletonList("id"), false, cursor).outcome();
        return new DatastoreQueryResult(out.getPointer(), out.getList().stream().map(e -> (String) e.get("id")).collect(Collectors.toList()));
    }

    @Override
    public RecordInfoQueryResult<RecordIdAndKind> getAllRecordIdAndKind(Integer limit, String cursor) {
        throw new NotImplementedException();
    }

    @Override
    public RecordInfoQueryResult<RecordId> getAllRecordIdsFromKind(Integer limit, String cursor, String kind) {
        throw new  NotImplementedException();
    }

    @Override
    public HashMap<String, Long> getActiveRecordsCount() {
        throw new  NotImplementedException();
    }

    @Override
    public Map<String, Long> getActiveRecordsCountForKinds(List<String> kinds) {
        throw new  NotImplementedException();
    }

    private int getLimitTuned(Integer limit) {
        return limit == null ? PAGE_SIZE : (limit > 0 ? limit : PAGE_SIZE);
    }

    private Destination getDestination() {
        return Destination.builder().partitionId(tenantInfo.getDataPartitionId())
            .namespace(new Namespace(tenantInfo.getName()))
            .kind(RECORD_KIND)
            .build();
    }
}
