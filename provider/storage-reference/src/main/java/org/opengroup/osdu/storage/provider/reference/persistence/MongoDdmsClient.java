package org.opengroup.osdu.storage.provider.reference.persistence;

import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.provider.reference.util.MongoClientHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MongoDdmsClient {

  @Autowired
  private MongoClientHandler mongoClientHandler;

  @Autowired
  private TenantInfo tenantInfo;

  public MongoCollection<Document> getMongoCollection(String dbName, String collectionName) {
    return mongoClientHandler.getMongoClient().getDatabase(dbName)
        .getCollection(collectionName);
  }
}
