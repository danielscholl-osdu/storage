// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.cache;

import org.opengroup.osdu.azure.di.RedisAzureConfiguration;
import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.inject.Named;

@Component
@ConditionalOnProperty(value = "runtime.env.local", havingValue = "false", matchIfMissing = true)
public class SchemaRedisCache extends RedisAzureCache<String, Schema> {

    public SchemaRedisCache(
            final @Named("REDIS_PORT") int port,
            final @Named("SCHEMA_REDIS_TTL") int timeout,
            final @Named("REDIS_EXPIRATION") int expiration,
            final @Named("REDIS_HOST_KEY") String hostKey,
            final @Named("REDIS_PASSWORD_KEY") String passwordKey,
            @Value("${redis.database}") final int database,
            @Value("${redis.command.timeout}") final int commandTimeout)
    {
        super(String.class, Schema.class, new RedisAzureConfiguration(database, expiration, port, timeout,
                commandTimeout, hostKey, passwordKey));
    }
}
