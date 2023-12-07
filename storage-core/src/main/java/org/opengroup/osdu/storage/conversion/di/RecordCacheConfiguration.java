package org.opengroup.osdu.storage.conversion.di;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.inject.Named;

@Configuration
@Getter
@Lazy
public class RecordCacheConfiguration {

    @Value("${record.cache.timeout:5}")
    private int cacheTimeOut;

    @Bean
    @Named("RECORD_CACHE_TIMEOUT")
    public int getRecordCacheTimeout() {
        return cacheTimeOut;
    }
}
