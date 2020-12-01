package org.opengroup.osdu.storage.provider.reference.util;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MongoClientHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MongoClientHandler.class);
  private static final String MONGO_PREFIX = "mongodb://";
  private static final String MONGO_OPTIONS = "retryWrites=true&w=majority&maxIdleTimeMS=10000";

  private com.mongodb.client.MongoClient mongoClient = null;

  @Value("${mongo.db.url:#{null}}")
  private String dbUrl;

  @Value("${mongo.db.apikey:#{null}}")
  private String apiKey;

  @Value("${mongo.db.user:#{null}}")
  private String dbUser;

  @Value("${mongo.db.password:#{null}}")
  private String dbPassword;

  private MongoClient getOrInitMongoClient() throws RuntimeException {
    if (mongoClient != null) {
      return mongoClient;
    }

    final String connectionString = String.format("%s%s:%s@%s/?%s",
        MONGO_PREFIX,
        dbUser,
        dbPassword,
        dbUrl,
        MONGO_OPTIONS);
    ConnectionString connString = new ConnectionString(connectionString);
    MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(connString)
        .retryWrites(true)
        .build();
    try {
      mongoClient = MongoClients.create(settings);
    } catch (Exception ex) {
      LOG.error("Error connecting MongoDB", ex);
      throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error connecting MongoDB",
          ex.getMessage(), ex);
    }
    return mongoClient;
  }

  public MongoClient getMongoClient() {
    if (mongoClient == null) {
      getOrInitMongoClient();
    }
    return mongoClient;
  }

}
