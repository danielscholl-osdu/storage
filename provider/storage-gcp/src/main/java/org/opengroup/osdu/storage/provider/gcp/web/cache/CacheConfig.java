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
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final GcpAppServiceConfig gcpAppServiceConfig;

    @Bean
    public GroupCache groupCache() {
        String redisGroupHost = gcpAppServiceConfig.getRedisGroupHost();
        Integer redisGroupPort = gcpAppServiceConfig.getRedisGroupPort();
        String redisGroupPassword = gcpAppServiceConfig.getRedisGroupPassword();
        Integer redisGroupExpiration = gcpAppServiceConfig.getRedisGroupExpiration();
        Boolean redisGroupWithSsl = gcpAppServiceConfig.getRedisGroupWithSsl();

        return redisGroupPassword != null
            ? new GroupCache(redisGroupHost, redisGroupPort, redisGroupPassword, redisGroupExpiration, redisGroupWithSsl)
            : new GroupCache(redisGroupHost, redisGroupPort, redisGroupExpiration);
    }

    @Bean("LegalTagCache")
    public LegalTagCache legalTagCache() {
        String redisStorageHost = gcpAppServiceConfig.getRedisStorageHost();
        Integer redisStoragePort = gcpAppServiceConfig.getRedisStoragePort();
        String redisStoragePassword = gcpAppServiceConfig.getRedisStoragePassword();
        Integer redisStorageExpiration = gcpAppServiceConfig.getRedisStorageExpiration();
        Boolean redisStorageWithSsl = gcpAppServiceConfig.getRedisStorageWithSsl();

        return redisStoragePassword != null
            ? new LegalTagCache(redisStorageHost, redisStoragePort, redisStoragePassword, redisStorageExpiration, redisStorageWithSsl)
            : new LegalTagCache(redisStorageHost, redisStoragePort, redisStorageExpiration);
    }

    @Bean
    public SchemaCache schemaCache() {
        String redisStorageHost = gcpAppServiceConfig.getRedisStorageHost();
        Integer redisStoragePort = gcpAppServiceConfig.getRedisStoragePort();
        String redisStoragePassword = gcpAppServiceConfig.getRedisStoragePassword();
        Integer redisStorageExpiration = gcpAppServiceConfig.getRedisStorageExpiration();
        Boolean redisStorageWithSsl = gcpAppServiceConfig.getRedisStorageWithSsl();

        return redisStoragePassword != null
            ? new SchemaCache(redisStorageHost, redisStoragePort, redisStoragePassword, redisStorageExpiration, redisStorageWithSsl)
            : new SchemaCache(redisStorageHost, redisStoragePort, redisStorageExpiration);
    }
}
