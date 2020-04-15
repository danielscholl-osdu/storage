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

package org.opengroup.osdu.storage.provider.byoc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.common.reflect.TypeToken;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;

@Repository
public class SchemaRepositoryImpl implements ISchemaRepository {
    public static Map<String, Map<String, String>> memMap = new HashMap<>();

    @Override
    public void add(Schema schema, String user) {
        // kind: SCHEMA_KIND; collection
        // key: schema.getKind(); doc id
        // USER: user;
        // SCHEMA: schema.getSchema(); in JSON
        // EXTENSION: schema.getExt();
        Gson gson = new Gson();

        String key = schema.getKind();

        String schemaJson = gson.toJson(schema.getSchema());
        String extJson = gson.toJson(schema.getExt());
        JsonObject doc = new JsonObject();
        doc.addProperty(USER, user);
        doc.addProperty(SCHEMA, schemaJson);
        doc.addProperty(EXTENSION, extJson);
        insertDoc(SCHEMA_KIND, key, doc.toString());
    }

    @Override
    public Schema get(String kind) {
        Gson gson = new Gson();
        JsonParser parser = new JsonParser();

        String docJson = memMap.get(SCHEMA_KIND).get(kind);
        JsonObject doc = parser.parse(docJson).getAsJsonObject();

        String schemaString = doc.get(SCHEMA).getAsString();
        SchemaItem[] schemaItems = gson.fromJson(schemaString, SchemaItem[].class);

        Map<String, Object> ext = null;

        if (!doc.get(EXTENSION).isJsonNull()) {
            String extString = doc.get(EXTENSION).getAsString();

            Type mapType = new TypeToken<Map<String, Object>>() {
            }.getType();

            ext = gson.fromJson(extString, mapType);
        }

        Schema newSchema = new Schema();
        newSchema.setKind(kind);
        newSchema.setSchema(schemaItems);
        newSchema.setExt(ext);
        return newSchema;
    }

    @Override
    public void delete(String kind) {
        memMap.get(SCHEMA_KIND).remove(kind);
    }

    private void insertDoc(String collection, String key, String doc)
    {
        if (memMap.containsKey(collection) && memMap.get(collection).containsKey(key))
            throw new IllegalArgumentException("Schema " + key + " already exist in collection " + collection);
        if (memMap.containsKey(collection)) {
            memMap.get(collection).put(key, doc);
        }
        else {
            Map<String, String> memDoc = new HashMap<>();
            memDoc.put(key, doc);
            memMap.put(collection, memDoc);
        }
    }
}

