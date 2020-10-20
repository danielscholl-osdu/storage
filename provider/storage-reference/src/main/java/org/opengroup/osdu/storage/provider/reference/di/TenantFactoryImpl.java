package org.opengroup.osdu.storage.provider.reference.di;

import static org.opengroup.osdu.storage.provider.reference.repository.SchemaRepositoryImpl.SCHEMA_DATABASE;

import com.google.gson.Gson;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bson.Document;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.reference.model.TenantInfoDocument;
import org.opengroup.osdu.storage.provider.reference.persistence.MongoDdmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantFactoryImpl implements ITenantFactory {

  private static final Logger LOG = LoggerFactory.getLogger(TenantFactoryImpl.class);
  public static final String TENANT_INFO = "TenantInfo";

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
        .getMongoCollection(SCHEMA_DATABASE, TENANT_INFO);
    FindIterable<Document> results = mongoCollection.find();
    if (Objects.isNull(results) && Objects.isNull(results.first())) {
      LOG.error(String.format("Collection \'%s\' is empty.", results));
    }
    for (Document document : results) {
      TenantInfoDocument tenantInfoDocument = new Gson()
          .fromJson(document.toJson(), TenantInfoDocument.class);
      TenantInfo tenantInfo = convertToTenantInfo(tenantInfoDocument);
      this.tenants.put(tenantInfo.getName(), tenantInfo);
    }
  }

  private TenantInfo convertToTenantInfo(TenantInfoDocument tenantInfoDocument) {
    TenantInfo tenantInfo = new TenantInfo();
    tenantInfo.setName(tenantInfoDocument.getId());
    return tenantInfo;
  }
}

