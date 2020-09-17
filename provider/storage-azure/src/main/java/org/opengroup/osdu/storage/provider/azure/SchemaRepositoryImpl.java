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

package org.opengroup.osdu.storage.provider.azure;

import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@Primary
public class SchemaRepositoryImpl implements ISchemaRepository {

    @Autowired
    private CosmosDBSchema db;

    @Override
    public void add(Schema schema, String user) {
        String kind = schema.getKind();
        if (db.findById(kind).isPresent())
            throw new IllegalArgumentException("Schema " + kind + " already exist. Can't create again.");

        SchemaDoc sd = new SchemaDoc();
        sd.setKind(kind);
        sd.setExtension(schema.getExt());
        sd.setUser(user);
        sd.setSchemaItems(schema.getSchema());
        db.save(sd);
    }

    @Override
    public Schema get(String kind) {
        Optional<SchemaDoc> sd = db.findById(kind);
        if (!sd.isPresent())
            return null;

        Schema newSchema = new Schema();
        newSchema.setKind(kind);
        newSchema.setSchema(sd.get().getSchemaItems());
        newSchema.setExt(sd.get().getExtension());
        return newSchema;
    }

    @Override
    public void delete(String kind) {
        SchemaDoc sd = new SchemaDoc();
        sd.setKind(kind);
        db.delete(sd);
    }

}
