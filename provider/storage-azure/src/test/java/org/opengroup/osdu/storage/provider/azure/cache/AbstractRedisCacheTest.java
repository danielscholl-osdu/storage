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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.opengroup.osdu.azure.cache.RedisAzureCache;
import org.opengroup.osdu.storage.provider.azure.di.RedisConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for Redis cache implementations.
 * Provides common test scenarios for all cache types.
 *
 * @param <T> The cache type being tested, must extend RedisAzureCache
 */
public abstract class AbstractRedisCacheTest<T extends RedisAzureCache<?>> {

    private static final int DEFAULT_DATABASE = 0;
    private static final int DEFAULT_COMMAND_TIMEOUT = 5;
    private static final int DEFAULT_PORT = 6380;
    private static final int DEFAULT_EXPIRATION = 15;
    private static final String DEFAULT_HOST_KEY = "test-redis-hostname";
    private static final String DEFAULT_PASSWORD_KEY = "test-redis-password";
    private static final String DEFAULT_PRINCIPAL_ID = "test-principal-id";
    private static final String DEFAULT_HOSTNAME = "primary.redis.cache";
    private static final long DEFAULT_TTL = 100L;

    /**
     * Create a cache instance using the provided RedisConfig.
     * Subclasses implement this to instantiate their specific cache type.
     */
    protected abstract T createCache(RedisConfig config);

    @Test
    @DisplayName("Should instantiate cache with all required parameters")
    void shouldInstantiateWithAllParameters() {
        T cache = assertDoesNotThrow(() -> createCache(defaultConfig()));
        assertNotNull(cache);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should instantiate cache with null or empty principal ID for backward compatibility")
    void shouldInstantiateWithNullOrEmptyPrincipalId(String principalId) {
        RedisConfig config = createConfig(principalId, DEFAULT_HOSTNAME);
        T cache = assertDoesNotThrow(() -> createCache(config));
        assertNotNull(cache);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Should handle null or empty hostname value")
    void shouldHandleNullOrEmptyHostname(String hostname) {
        RedisConfig config = createConfig(DEFAULT_PRINCIPAL_ID, hostname);
        T cache = assertDoesNotThrow(() -> createCache(config));
        assertNotNull(cache);
    }

    private RedisConfig defaultConfig() {
        return createConfig(DEFAULT_PRINCIPAL_ID, DEFAULT_HOSTNAME);
    }

    private RedisConfig createConfig(String principalId, String hostname) {
        return new RedisConfig(
            DEFAULT_DATABASE,
            DEFAULT_COMMAND_TIMEOUT,
            DEFAULT_PORT,
            DEFAULT_EXPIRATION,
            DEFAULT_HOST_KEY,
            DEFAULT_PASSWORD_KEY,
            principalId,
            hostname,
            DEFAULT_TTL,
            DEFAULT_TTL,
            DEFAULT_TTL);
    }
}
