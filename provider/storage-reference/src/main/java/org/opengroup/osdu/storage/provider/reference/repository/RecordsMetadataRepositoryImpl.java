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
import static com.mongodb.util.JSON.serialize;
import static org.opengroup.osdu.storage.provider.reference.repository.SchemaRepositoryImpl.SCHEMA_DATABASE;

import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bson.Document;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.reference.model.RecordMetadataDocument;
import org.opengroup.osdu.storage.provider.reference.persistence.MongoDdmsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class RecordsMetadataRepositoryImpl implements IRecordsMetadataRepository<String> {

  public static final String STORAGE_RECORD = "StorageRecord";
  private final MongoDdmsClient mongoDdmsClient;

  @Autowired
  public RecordsMetadataRepositoryImpl(MongoDdmsClient mongoDdmsClient) {
    this.mongoDdmsClient = mongoDdmsClient;
  }

  @Override
  public List<RecordMetadata> createOrUpdate(List<RecordMetadata> recordsMetadata) {
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, STORAGE_RECORD);
    if (Objects.nonNull(recordsMetadata)) {
      for (RecordMetadata recordMetadata : recordsMetadata) {
        RecordMetadataDocument recordMetadataDocument = convertToRecordMetadataDocument(
            recordMetadata);
        mongoCollection.replaceOne(eq("id", recordMetadataDocument.getId()),
            Document.parse(new Gson().toJson(recordMetadataDocument)),
            (new UpdateOptions()).upsert(true));
      }
    }
    return recordsMetadata;
  }

  @Override
  public void delete(String id) {
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, STORAGE_RECORD);
    mongoCollection.deleteOne(eq("id", id));
  }

  @Override
  public RecordMetadata get(String id) {
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, STORAGE_RECORD);
    Document doc = mongoCollection.find(eq("id", id)).first();
    if (Objects.isNull(doc)) {
      return null;
    }
    RecordMetadataDocument recordMetadataDocument = new Gson()
        .fromJson(serialize(doc), RecordMetadataDocument.class);
    return convertToRecordMetadata(recordMetadataDocument);
  }

  @Override
  public Map<String, RecordMetadata> get(List<String> ids) {
    Map<String, RecordMetadata> output = new HashMap<>();
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, STORAGE_RECORD);
    for (String id : ids) {
      Document document = mongoCollection.find(eq("id", id)).first();
      RecordMetadataDocument recordMetadataDocument = null;
      if (Objects.nonNull(document)) {
        recordMetadataDocument = new Gson()
            .fromJson(serialize(document), RecordMetadataDocument.class);
      }
      RecordMetadata rmd = convertToRecordMetadata(recordMetadataDocument);
      if (Objects.isNull(rmd)) {
        continue;
      }
      output.put(id, rmd);
    }
    return output;
  }

  @Override
  public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegalTagName(
      String legalTagName, int limit, String cursor) {
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, STORAGE_RECORD);
    List<RecordMetadata> outputRecords = new ArrayList<>();
    FindIterable<Document> results = mongoCollection.find().skip(limit * (limit - 1)).limit(limit);
    for (Document document : results) {
      RecordMetadataDocument recordMetadataDocument = new Gson()
          .fromJson(serialize(document), RecordMetadataDocument.class);
      if (Objects.nonNull(recordMetadataDocument)) {
        if (recordMetadataDocument.getLegal().getLegaltags().contains(legalTagName)) {
          RecordMetadata recordMetadata = convertToRecordMetadata(recordMetadataDocument);
          outputRecords.add(recordMetadata);
        }
      }
    }
    return new AbstractMap.SimpleEntry<>(cursor, outputRecords);
  }

  @Override
  public AbstractMap.SimpleEntry<String, List<RecordMetadata>> queryByLegal(String legalTagName,
      LegalCompliance status, int limit) {
    return null;
  }

  private RecordMetadataDocument convertToRecordMetadataDocument(RecordMetadata recordMetadata) {
    RecordMetadataDocument recordMetadataDocument = new RecordMetadataDocument();
    recordMetadataDocument.setId(recordMetadata.getId());
    recordMetadataDocument.setAcl(recordMetadata.getAcl());
    recordMetadataDocument.setAncestry(recordMetadata.getAncestry());
    recordMetadataDocument.setCreateTime(recordMetadata.getCreateTime());
    recordMetadataDocument.setModifyTime(recordMetadata.getModifyTime());
    recordMetadataDocument.setGcsVersionPaths(recordMetadata.getGcsVersionPaths());
    recordMetadataDocument.setKind(recordMetadata.getKind());
    recordMetadataDocument.setLegal(recordMetadata.getLegal());
    recordMetadataDocument.setModifyUser(recordMetadata.getModifyUser());
    recordMetadataDocument.setStatus(recordMetadata.getStatus());
    recordMetadataDocument.setUser(recordMetadata.getUser());

    return recordMetadataDocument;
  }

  private RecordMetadata convertToRecordMetadata(RecordMetadataDocument recordMetadataDocument) {
    if (Objects.isNull(recordMetadataDocument)) {
      return null;
    }
    RecordMetadata recordMetadata = new RecordMetadata();
    recordMetadata.setId(recordMetadataDocument.getId());
    recordMetadata.setAcl(recordMetadataDocument.getAcl());
    recordMetadata.setAncestry(recordMetadataDocument.getAncestry());
    recordMetadata.setCreateTime(recordMetadataDocument.getCreateTime());
    recordMetadata.setModifyTime(recordMetadataDocument.getModifyTime());
    recordMetadata.setGcsVersionPaths(recordMetadataDocument.getGcsVersionPaths());
    recordMetadata.setKind(recordMetadataDocument.getKind());
    recordMetadata.setLegal(recordMetadataDocument.getLegal());
    recordMetadata.setModifyUser(recordMetadataDocument.getModifyUser());
    recordMetadata.setStatus(recordMetadataDocument.getStatus());
    recordMetadata.setUser(recordMetadataDocument.getUser());

    return recordMetadata;
  }
}

