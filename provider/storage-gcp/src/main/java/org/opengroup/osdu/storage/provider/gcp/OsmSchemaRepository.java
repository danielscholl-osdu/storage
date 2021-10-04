// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.provider.gcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.gcp.osm.model.Destination;
import org.opengroup.osdu.core.gcp.osm.model.Namespace;
import org.opengroup.osdu.core.gcp.osm.model.Query;
import org.opengroup.osdu.core.gcp.osm.service.Context;
import org.opengroup.osdu.core.gcp.osm.service.Transaction;
import org.opengroup.osdu.core.gcp.osm.translate.TranslatorException;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

import static org.opengroup.osdu.core.gcp.osm.model.where.predicate.Eq.eq;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Repository
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "osmDriver", havingValue = "datastore")
@Log
@RequiredArgsConstructor
public class OsmSchemaRepository implements ISchemaRepository {

    private final Context context;
    private final TenantInfo tenantInfo;

    private Destination getDestination() {
        Destination destination = Destination.builder().partitionId(tenantInfo.getDataPartitionId())
                .namespace(new Namespace(tenantInfo.getName()))
                .kind(OsmRecordsMetadataRepository.SCHEMA_KIND).build();
        return destination;
    }

    @Override
    public void add(Schema schema, String user) {

        Transaction txn = context.getTransaction();

        Query q = Query.builder(Schema.class).destination(getDestination()).where(eq("kind", schema.getKind())).build();
        try {
            if (context.findOne(q).isPresent()) {
                txn.rollbackIfActive();
                throw new IllegalArgumentException("A schema for the specified kind has already been registered.");
            } else {
                //builder.set(USER, user);
                context.create(schema, getDestination());
                txn.commitIfActive();
            }
        } catch (TranslatorException e) {
            log.throwing(this.getClass().getName(), "add", e);
            throw new RuntimeException("OSM TranslatorException", e);
        } finally {
            txn.rollbackIfActive();
        }
    }

    @Override
    public Schema get(String kind) {
        try {
            Query<Schema> q = Query.builder(Schema.class).destination(getDestination()).where(eq("kind", kind)).build();
            return context.getResultsAsList(q).stream().findFirst().orElse(null);
        } catch (TranslatorException e) {
            log.throwing(this.getClass().getName(), "get", e);
            throw new RuntimeException("OSM TranslatorException", e);
        }
    }

    @Override
    public void delete(String kind) {
        try {
            Query<Schema> q = Query.builder(Schema.class).destination(getDestination()).where(eq("kind", kind)).build();
            context.delete(q);
        } catch (TranslatorException e) {
            log.throwing(this.getClass().getName(), "delete", e);
            throw new RuntimeException("OSM TranslatorException", e);
        }
    }
}