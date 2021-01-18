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

package org.opengroup.osdu.storage.provider.reference.persistence;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.opengroup.osdu.storage.provider.reference.util.MongoClientHandler;
import org.springframework.stereotype.Component;

@Component
public class MongoDdmsClient {

  private MongoClientHandler mongoClientHandler;

  public MongoDdmsClient(MongoClientHandler mongoClientHandler) {
    this.mongoClientHandler = mongoClientHandler;
  }

  public MongoCollection<Document> getMongoCollection(String dbName, String collectionName) {
    return mongoClientHandler.getMongoClient().getDatabase(dbName)
        .getCollection(collectionName);
  }
}
