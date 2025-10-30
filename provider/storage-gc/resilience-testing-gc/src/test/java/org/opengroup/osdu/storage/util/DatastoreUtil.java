/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.DatastoreOptions.Builder;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;
import java.util.Collections;
import java.util.List;
import org.opengroup.osdu.storage.config.EnvironmentConfiguration;

public class DatastoreUtil {

  private static final String BUCKET_PROPERTY = "bucket";
  private final EnvironmentConfiguration configuration;
  private final Datastore datastore;

  public DatastoreUtil(EnvironmentConfiguration configuration) {
    this.configuration = configuration;
    Builder builder = DatastoreOptions.newBuilder()
        .setNamespace(configuration.getNamespace())
        .setProjectId(configuration.getProjectId());

    if (configuration.getDatabaseId() != null) {
      builder.setDatabaseId(configuration.getDatabaseId());
    }

    datastore = builder.build().getService();
  }

  //intentionally corrupt record, make
  // bucket ["osdu:test:test-error-handling:1.0.0/osdu:test-error-handling:1"]
  // property look like
  // bucket []
  public void deleteVersionsFromRecordMeta(List<String> ids) {
    Transaction txn = datastore.newTransaction();
    KeyFactory keyFactory = new KeyFactory(configuration.getProjectId(),
        configuration.getNamespace());
    try {
      for (String id : ids) {
        Key key = keyFactory.setKind(configuration.getKind()).newKey(id);
        Entity storageRecord = Entity.newBuilder(txn.get(key)).set(BUCKET_PROPERTY, Collections.emptyList()).build();
        txn.put(storageRecord);
      }
      txn.commit();
    } finally {
      if (txn.isActive()) {
        txn.rollback();
      }
    }
  }
}
