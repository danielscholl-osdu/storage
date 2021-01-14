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

package org.opengroup.osdu.storage.provider.azure.repository;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.storage.provider.azure.SchemaDoc;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public class SchemaRepository extends SimpleCosmosStoreRepository<SchemaDoc> implements ISchemaRepository {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;

    @Autowired
    private CosmosContainerConfig cosmosContainerConfig;

    @Autowired
    private String schemaCollection;

    @Autowired
    private String cosmosDBName;

    @Autowired
    private JaxRsDpsLog logger;

    public SchemaRepository() {
        super(SchemaDoc.class);
    }

    @Override
    public synchronized void add(Schema schema, String user) {
        Assert.notNull(schema, "schema must not be null");
        Assert.notNull(user, "user must not be null");
        String kind = schema.getKind();
        if (this.exists(headers.getPartitionId(), cosmosDBName, schemaCollection, kind, kind)) {
            throw new IllegalArgumentException("Schema " + kind + " already exists. Can't create again.");
        }
        SchemaDoc sd = new SchemaDoc();
        sd.setKind(kind);
        sd.setId(kind);
        sd.setExtension(schema.getExt());
        sd.setUser(user);
        sd.setSchemaItems(schema.getSchema());
        this.upsertItem(headers.getPartitionId(), cosmosDBName, schemaCollection, sd.getId(), sd);
    }

    @Override
    public Schema get(String kind) {
        SchemaDoc item = this.getOne(kind, headers.getPartitionId(), cosmosDBName, schemaCollection, kind);
        return (item == null) ? null : map(item);
    }

    public Schema map(SchemaDoc item) {
        Schema schema = new Schema();
        schema.setKind(item.getKind());
        schema.setSchema(item.getSchemaItems());
        schema.setExt(item.getExtension());
        return schema;
    }

    @Override
    public void delete(String id) {
        this.deleteById(id, headers.getPartitionId(), cosmosDBName, schemaCollection, id);
    }

    public Iterable<SchemaDoc> findAll(@NonNull Sort sort) {
        return this.findAll(sort, headers.getPartitionId(), cosmosDBName, schemaCollection);
    }

    public Page<SchemaDoc> findAll(@NonNull Pageable pageable) {
        return this.findAll(pageable, headers.getPartitionId(), cosmosDBName, schemaCollection);
    }

}