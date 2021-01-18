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

package org.opengroup.osdu.storage.provider.reference.repository;

import static com.mongodb.client.model.Filters.eq;

import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.bson.Document;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.provider.reference.model.SchemaDocument;
import org.opengroup.osdu.storage.provider.reference.persistence.MongoDdmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SchemaRepositoryImpl implements ISchemaRepository {

  private static final Logger logger = LoggerFactory.getLogger(SchemaRepositoryImpl.class);
  public static final String SCHEMA_STORAGE = "SchemaStorage";
  public static final String RECORD_STORAGE = "StorageRecord";
  public static final String SCHEMA_DATABASE = "schema";

  private MongoDdmsClient mongoClient;

  @Autowired
  public SchemaRepositoryImpl(MongoDdmsClient mongoClient) {
    this.mongoClient = mongoClient;
  }

  @Override
  public void add(Schema schema, String user) {
    MongoCollection collection = this.mongoClient
        .getMongoCollection(SCHEMA_DATABASE, SCHEMA_STORAGE);
    String kind = schema.getKind();
    FindIterable<Document> results = collection.find(eq("kind", kind));
    if (Objects.nonNull(results) && Objects.nonNull(results.first())) {
      throw new IllegalArgumentException("Schema " + kind + " already exist. Can't create again.");
    }
    SchemaDocument schemaDocument = new SchemaDocument(schema, user);
    collection.insertOne(Document.parse(new Gson().toJson(schemaDocument)));
  }

  @Override
  public Schema get(String kind) {
    MongoCollection collection = this.mongoClient
        .getMongoCollection(SCHEMA_DATABASE, SCHEMA_STORAGE);
    Document record = (Document) collection.find(eq("kind", kind)).first();
    if (Objects.isNull(record)) {
      throw new AppException(
          HttpStatus.SC_NOT_FOUND, "Not found",
          String.format("Schema with id %s does not exist.", kind));
    }
    SchemaDocument schemaDocument = new Gson().fromJson(record.toJson(), SchemaDocument.class);
    return convertToSchemaEntity(schemaDocument);
  }

  @Override
  public void delete(String kind) {
    MongoCollection collection = this.mongoClient
        .getMongoCollection(SCHEMA_DATABASE, SCHEMA_STORAGE);
    collection.deleteOne(eq("kind", kind));
  }

  private Schema convertToSchemaEntity(SchemaDocument schemaDocument) {
    Schema schema = new Schema();
    schema.setKind(schemaDocument.getKind());
    schema.setExt(schemaDocument.getExtension());
    schema.setSchema(schemaDocument.getSchema());
    return schema;
  }
}
