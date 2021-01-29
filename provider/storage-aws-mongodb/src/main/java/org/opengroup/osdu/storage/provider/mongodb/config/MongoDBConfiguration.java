package org.opengroup.osdu.storage.provider.mongodb.config;

import com.mongodb.MongoClient;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.aws.mongodb.MongoDBHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.annotation.Nonnull;

@AllArgsConstructor
@NoArgsConstructor
@ComponentScan("org.opengroup.osdu.storage.provider")
@Configuration
public class MongoDBConfiguration { //extends AbstractMongoConfiguration

    @Value("${aws.mongodb.database.name}")
    private String databaseName;
    @Value("${aws.mongodb.connection.url}")
    private String connectionString;

    @Bean
    public MongoDBHelper queryHelper() {
        return new MongoDBHelper(connectionString, databaseName);
    }

}
