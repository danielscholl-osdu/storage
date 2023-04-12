package org.opengroup.osdu.storage.provider.azure.di;

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

    @Value("${redis.cursor.ttl:90}")
    public int cursorRedisTtl;

    @Value("${redis.expiration:3600}")
    public int redisExpiration;

    @Value("${redis.host.key}")
    public String redisHostKey;

    @Value("${redis.password.key}")
    public String redisPasswordKey;

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
    @Named("CURSOR_REDIS_TTL")
    public int getCursorRedisTtl() { return cursorRedisTtl; }

    @Bean
    @Named("REDIS_EXPIRATION")
    public int getRedisExpiration() { return redisExpiration; }

    @Bean
    @Named("REDIS_HOST_KEY")
    public String getRedisHostKey() {
        return redisHostKey;
    }

    @Bean
    @Named("REDIS_PASSWORD_KEY")
    public String getRedisPasswordKey() {
        return redisPasswordKey;
    }
}
