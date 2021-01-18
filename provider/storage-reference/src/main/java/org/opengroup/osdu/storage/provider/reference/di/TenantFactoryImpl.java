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

package org.opengroup.osdu.storage.provider.reference.di;

import static java.util.Objects.isNull;

import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.reference.persistence.MongoDdmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantFactoryImpl implements ITenantFactory {

  private static final Logger LOG = LoggerFactory.getLogger(TenantFactoryImpl.class);
  public static final String TENANT_INFO = "tenantinfo";
  public static final String MAIN_DATABASE = "main";

  @Autowired
  private MongoDdmsClient mongoClient;

  private Map<String, TenantInfo> tenants;

  public boolean exists(String tenantName) {
    if (this.tenants == null) {
      initTenants();
    }
    return this.tenants.containsKey(tenantName);
  }

  public TenantInfo getTenantInfo(String tenantName) {
    if (this.tenants == null) {
      initTenants();
    }
    return this.tenants.get(tenantName);
  }

  public Collection<TenantInfo> listTenantInfo() {
    if (this.tenants == null) {
      initTenants();
    }
    return this.tenants.values();
  }

  public <V> ICache<String, V> createCache(String tenantName, String host, int port,
      int expireTimeSeconds, Class<V> classOfV) {
    return null;
  }

  public void flushCache() {
  }

  private void initTenants() {
    this.tenants = new HashMap<>();
    MongoCollection<Document> mongoCollection = mongoClient
        .getMongoCollection(MAIN_DATABASE, TENANT_INFO);
    FindIterable<Document> results = mongoCollection.find();
    if (isNull(results) || isNull(results.first())) {
      LOG.error(String.format("Collection \'%s\' is empty.", results));
    }
    for (Document document : results) {
      TenantInfo tenantInfo = new Gson().fromJson(document.toJson(), TenantInfo.class);
      ObjectId id = (ObjectId) document.get("_id");
      tenantInfo.setId((long) id.getCounter());
      tenantInfo.setCrmAccountIds((ArrayList<String>) document.get("crmAccountID"));
      this.tenants.put(tenantInfo.getName(), tenantInfo);
    }
  }
}

