/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.gcp.osm.model.Destination;
import org.opengroup.osdu.core.gcp.osm.model.Kind;
import org.opengroup.osdu.core.gcp.osm.model.Namespace;
import org.opengroup.osdu.core.gcp.osm.model.query.GetQuery;
import org.opengroup.osdu.core.gcp.osm.service.Context;
import org.opengroup.osdu.core.gcp.osm.service.Transaction;
import org.opengroup.osdu.core.gcp.osm.translate.Outcome;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import java.util.*;

import static org.opengroup.osdu.core.gcp.osm.model.where.condition.And.and;
import static org.opengroup.osdu.core.gcp.osm.model.where.predicate.Eq.eq;
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
    public static final String LEGAL_TAGS = "legal.legaltags";
    public static final String LEGAL_COMPLIANCE = "legal.status";
    public static final String STATUS = "status";


    private Destination getDestination() {
        return Destination.builder().partitionId(tenantInfo.getDataPartitionId())
                .namespace(new Namespace(tenantInfo.getName())).kind(RECORD_KIND).build();
    }

    @Override
    public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
        if (recordsMetadata != null) {
            Transaction txn = context.beginTransaction(getDestination());
            try {
                for (RecordMetadata recordMetadata : recordsMetadata) {
                    context.upsert(recordMetadata, getDestination());
                }
                txn.commitIfActive();
            } finally {
                txn.rollbackIfActive();
            }
        }
        return recordsMetadata;
    }

    @Override
    public void delete(String id) {
        context.deleteById(RecordMetadata.class, getDestination(), id);
    }

    @Override
    public RecordMetadata get(String id) {
        GetQuery<RecordMetadata> osmQuery = new GetQuery<>(RecordMetadata.class, getDestination(), eq("id", id));
        return context.getResultsAsList(osmQuery).stream().findFirst().orElse(null);
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
    public Map<String, RecordMetadata> get(List<String> ids) {

        Map<String, RecordMetadata> output = new HashMap<>();
        for (String id : ids) {
            Optional.ofNullable(get(id)).ifPresent(r -> output.put(id, r));
        }
        return output;
    }

    //TODO remove when other providers replace with new method queryByLegal
    @Override
    public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(String legalTagName, int limit, String cursor) {
        return queryByLegal(legalTagName, null, limit);
    }
}