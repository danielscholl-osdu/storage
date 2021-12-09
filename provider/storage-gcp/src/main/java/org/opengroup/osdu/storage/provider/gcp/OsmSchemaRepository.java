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
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.gcp.osm.model.Destination;
import org.opengroup.osdu.core.gcp.osm.model.Namespace;
import org.opengroup.osdu.core.gcp.osm.model.query.GetQuery;
import org.opengroup.osdu.core.gcp.osm.service.Context;
import org.opengroup.osdu.core.gcp.osm.service.Transaction;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import static org.opengroup.osdu.core.gcp.osm.model.where.predicate.Eq.eq;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Repository
@Scope(SCOPE_SINGLETON)
@Log
@RequiredArgsConstructor
public class OsmSchemaRepository implements ISchemaRepository {

    private final Context context;
    private final TenantInfo tenantInfo;

    private Destination getDestination() {
        return Destination.builder().partitionId(tenantInfo.getDataPartitionId())
                .namespace(new Namespace(tenantInfo.getName())).kind(OsmRecordsMetadataRepository.SCHEMA_KIND).build();
    }

    @Override
    public void add(Schema schema, String user) {

        Transaction txn = null;

        GetQuery<Schema> q = new GetQuery<>(Schema.class, getDestination(), eq("kind", schema.getKind()));
        try {
            txn = context.beginTransaction(getDestination());
            if (context.findOne(q).isPresent()) {
                txn.rollbackIfActive();
                throw new IllegalArgumentException("A schema for the specified kind has already been registered.");
            } else {
                context.create(schema, getDestination());
                txn.commitIfActive();
            }
        } finally {
            if (txn != null) txn.rollbackIfActive();
        }
    }

    @Override
    public Schema get(String kind) {
        GetQuery<Schema> q = new GetQuery<>(Schema.class, getDestination(), eq("kind", kind));
        return context.getResultsAsList(q).stream().findFirst().orElse(null);
    }

    @Override
    public void delete(String kind) {
        context.deleteById(Schema.class, getDestination(), kind);
    }
}