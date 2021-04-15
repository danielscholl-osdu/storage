package org.opengroup.osdu.storage.provider.azure.di;

import com.azure.security.keyvault.secrets.SecretClient;
import org.opengroup.osdu.azure.KeyVaultFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.inject.Named;

@Configuration
public class RedisConfig {

    @Value("${redis.port:6380}")
    public int redisPort;

    @Value("${redis.schema.ttl:3600}")
    public int schemaRedisTtl;

    @Value("${redis.group.ttl:30}")
    public int groupRedisTtl;

    @Bean
    @Named("REDIS_PORT")
    public int getRedisPort() {
        return redisPort;
    }

    @Bean
    @Named("SCHEMA_REDIS_TTL")
    public int getSchemaRedisTtl() {
        return schemaRedisTtl;
    }

    @Bean
    @Named("GROUP_REDIS_TTL")
    public int getGroupRedisTtl() { return groupRedisTtl; }

    @Bean
    @Named("REDIS_HOST")
    public String redisHost(SecretClient kv) {
        return KeyVaultFacade.getSecretWithValidation(kv, "redis-hostname");
    }

    @Bean
    @Named("REDIS_PASSWORD")
    public String redisPassword(SecretClient kv) { return KeyVaultFacade.getSecretWithValidation(kv, "redis-password"); }
}
