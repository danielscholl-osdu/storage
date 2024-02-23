/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
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
import org.opengroup.osdu.core.cache.RedisCacheBuilder;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.partition.PartitionInfo;

import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final RedisCacheBuilder<String, String> legalRedisCacheBuilder;
    private final RedisCacheBuilder<String, Schema> schemaRedisCacheBuilder;

    @Bean
    public ICache<String, Groups> groupCache() {
        return new GroupCache();
    }

    @Bean("LegalTagCache")
    public ICache<String, String> legalTagCache(GcpAppServiceConfig gcpAppServiceConfig) {
        RedisCache<String, String> storageCache = legalRedisCacheBuilder.buildRedisCache(
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
    public RedisCache<String, Schema> schemaCache(GcpAppServiceConfig gcpAppServiceConfig) {
        return schemaRedisCacheBuilder.buildRedisCache(
            gcpAppServiceConfig.getRedisStorageHost(),
            gcpAppServiceConfig.getRedisStoragePort(),
            gcpAppServiceConfig.getRedisStoragePassword(),
            gcpAppServiceConfig.getRedisStorageExpiration(),
            gcpAppServiceConfig.getRedisStorageWithSsl(),
            String.class,
            Schema.class
        );
    }

    @Bean
    public ICache<String, PartitionInfo> partitionInfoCache() {
        return new VmCache<>(600, 2000);
    }
}
