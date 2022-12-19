/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.web.cache;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.gcp.cache.RedisCacheBuilder;
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {


    @Bean
    public ICache<String, Groups> groupCache(GcpAppServiceConfig gcpAppServiceConfig) {
        RedisCacheBuilder<String, Groups> cacheBuilder = new RedisCacheBuilder<>();
        return cacheBuilder.buildRedisCache(
            gcpAppServiceConfig.getRedisGroupHost(),
            gcpAppServiceConfig.getRedisGroupPort(),
            gcpAppServiceConfig.getRedisGroupPassword(),
            gcpAppServiceConfig.getRedisGroupExpiration(),
            gcpAppServiceConfig.getRedisGroupWithSsl(),
            String.class,
            Groups.class
        );
    }

    @Bean("LegalTagCache")
    public ICache<String, String> legalTagCache(GcpAppServiceConfig gcpAppServiceConfig) {
        RedisCacheBuilder<String, String> cacheBuilder = new RedisCacheBuilder<>();
        RedisCache<String, String> storageCache = cacheBuilder.buildRedisCache(
            gcpAppServiceConfig.getRedisStorageHost(),
            gcpAppServiceConfig.getRedisStoragePort(),
            gcpAppServiceConfig.getRedisStoragePassword(),
            gcpAppServiceConfig.getRedisStorageExpiration(),
            gcpAppServiceConfig.getRedisStorageWithSsl(),
            String.class,
            String.class
        );
        return new LegalTagMultiTenantCache(storageCache);
    }

    @Bean
    public ICache<String, Schema> schemaCache(GcpAppServiceConfig gcpAppServiceConfig) {
        RedisCacheBuilder<String, Schema> cacheBuilder = new RedisCacheBuilder<>();
        return cacheBuilder.buildRedisCache(
            gcpAppServiceConfig.getRedisStorageHost(),
            gcpAppServiceConfig.getRedisStoragePort(),
            gcpAppServiceConfig.getRedisStoragePassword(),
            gcpAppServiceConfig.getRedisStorageExpiration(),
            gcpAppServiceConfig.getRedisStorageWithSsl(),
            String.class,
            Schema.class
        );
    }
}
