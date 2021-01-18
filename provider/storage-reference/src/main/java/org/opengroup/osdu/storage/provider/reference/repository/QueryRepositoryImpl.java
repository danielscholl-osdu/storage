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
import static org.opengroup.osdu.storage.provider.reference.repository.SchemaRepositoryImpl.RECORD_STORAGE;
import static org.opengroup.osdu.storage.provider.reference.repository.SchemaRepositoryImpl.SCHEMA_DATABASE;
import static org.opengroup.osdu.storage.provider.reference.repository.SchemaRepositoryImpl.SCHEMA_STORAGE;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.opengroup.osdu.core.common.model.storage.DatastoreQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.IQueryRepository;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.provider.interfaces.ISchemaRepository;
import org.opengroup.osdu.storage.provider.reference.persistence.MongoDdmsClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class QueryRepositoryImpl implements IQueryRepository {
  private MongoDdmsClient mongoDdmsClient;

  @Autowired
  public QueryRepositoryImpl(MongoDdmsClient mongoDdmsClient) {
    this.mongoDdmsClient = mongoDdmsClient;
  }

  @Override
  public DatastoreQueryResult getAllKinds(Integer limit, String cursor) {
    int numRecords = PAGE_SIZE;
    if (limit != null) {
      numRecords = limit > 0 ? limit : PAGE_SIZE;
    }
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, SCHEMA_STORAGE);
    FindIterable<Document> results = mongoCollection.find()
        .limit(numRecords);
    List<String> kinds = new ArrayList<>();
    for (Document document : results) {
      kinds.add(document.get("kind").toString());
    }
    return new DatastoreQueryResult(cursor, kinds);
  }

  @Override
  public DatastoreQueryResult getAllRecordIdsFromKind(
      String kind, Integer limit, String cursor) {
    boolean paginated = false;

    int numRecords = PAGE_SIZE;
    if (Objects.nonNull(limit)) {
      numRecords = limit > 0 ? limit : PAGE_SIZE;
      paginated = true;
    }

    if (cursor != null && !cursor.isEmpty()) {
      paginated = true;
    }

    DatastoreQueryResult dqr = new DatastoreQueryResult();
    List<String> ids = new ArrayList();
    MongoCollection<Document> mongoCollection = mongoDdmsClient
        .getMongoCollection(SCHEMA_DATABASE, RECORD_STORAGE);
    FindIterable<Document> results = mongoCollection.find(eq("kind", kind))
        .limit(numRecords);
    for (Document document : results) {
      ids.add(document.get("id").toString());
    }
    dqr.setResults(ids);
    dqr.setCursor(cursor);
    return dqr;
  }

}

