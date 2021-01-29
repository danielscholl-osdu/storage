// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.mongodb;

import org.opengroup.osdu.core.aws.mongodb.MongoDBHelper;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.provider.mongodb.util.mongodb.documents.SchemaMongoDBDoc;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;

@Repository
public class SchemaRepositoryImplMongoDB implements ISchemaRepository {

    private DpsHeaders headers;
    private MongoDBHelper queryHelper;

    public SchemaRepositoryImplMongoDB(DpsHeaders headers, MongoDBHelper queryHelper) {
        this.headers = headers;
        this.queryHelper = queryHelper;
    }

    @PostConstruct
    public void init() {
        queryHelper.ensureIndex(SchemaMongoDBDoc.class, new Index().on("dataPartitionId", Sort.Direction.ASC));
        queryHelper.ensureIndex(SchemaMongoDBDoc.class, new Index().on("user", Sort.Direction.ASC));
    }

    @Override
    public void add(Schema schema, String user) {
        SchemaMongoDBDoc sd = new SchemaMongoDBDoc();
        sd.setKind(schema.getKind());

        // Check if a schema with this kind already exists
        if (queryHelper.keyExistsInTable(sd.getKind(), SchemaMongoDBDoc.class)) {
            throw new IllegalArgumentException("Schema " + sd.getKind() + " already exists. Can't create again.");
        }

        // Complete the SchemaDoc object and add it to the database
        sd.setExtension(schema.getExt());
        sd.setUser(user);
        sd.setSchemaItems(Arrays.asList(schema.getSchema()));
        sd.setDataPartitionId(headers.getPartitionId());
        queryHelper.save(sd);
    }

    @Override
    public Schema get(String kind) {
        SchemaMongoDBDoc sd = queryHelper.getById(kind, SchemaMongoDBDoc.class);
        if (sd == null) {
            return null;
        }

        // Create a Schema object and assign the values retrieved from MongoDB
        return mapSchema(sd);
    }

    @Override
    public void delete(String kind) {
        queryHelper.deleteByPrimaryKey(SchemaMongoDBDoc.class, kind);
    }

    private Schema mapSchema(SchemaMongoDBDoc schemaDoc) {
        Schema newSchema = new Schema();
        newSchema.setKind(schemaDoc.getKind());
        List<SchemaItem> schemaItemList = schemaDoc.getSchemaItems();
        //TODO: check for removing this
        SchemaItem[] schemaItemsArray = schemaItemList.toArray(new SchemaItem[0]); // convert List to Array
        newSchema.setSchema(schemaItemsArray);
        newSchema.setExt(schemaDoc.getExtension());
        return newSchema;
    }

}
