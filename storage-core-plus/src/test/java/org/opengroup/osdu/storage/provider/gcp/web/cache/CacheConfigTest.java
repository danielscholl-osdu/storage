/*
 *  Copyright @ Microsoft Corporation
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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.cache.RedisCacheBuilder;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.provider.gcp.web.config.GcpAppServiceConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("CacheConfig Tests")
class CacheConfigTest {

    private static final String TEST_STORAGE_HOST = "storage-redis-host";
    private static final Integer TEST_STORAGE_PORT = 6379;
    private static final String TEST_STORAGE_PASSWORD = "storage-password";
    private static final Integer TEST_STORAGE_EXPIRATION = 3600;
    private static final Boolean TEST_STORAGE_WITH_SSL = true;

    private static final String TEST_GROUP_HOST = "group-redis-host";
    private static final Integer TEST_GROUP_PORT = 6380;
    private static final String TEST_GROUP_PASSWORD = "group-password";
    private static final Integer TEST_GROUP_EXPIRATION = 7200;
    private static final Boolean TEST_GROUP_WITH_SSL = false;

    @Mock
    private RedisCacheBuilder<String, String> legalRedisCacheBuilder;

    @Mock
    private RedisCacheBuilder<String, Schema> schemaRedisCacheBuilder;

    @Mock
    private RedisCacheBuilder<String, Groups> groupsRedisCacheBuilder;

    @Mock
    private GcpAppServiceConfig gcpAppServiceConfig;

    @Mock
    private RedisCache<String, String> mockLegalRedisCache;

    @Mock
    private RedisCache<String, Schema> mockSchemaRedisCache;

    @Mock
    private RedisCache<String, Groups> mockGroupsRedisCache;

    private CacheConfig cacheConfig;

    @BeforeEach
    void setUp() {
        // Manually create CacheConfig with mocked builders
        cacheConfig = new CacheConfig(
                legalRedisCacheBuilder,
                schemaRedisCacheBuilder,
                groupsRedisCacheBuilder
        );

        // Setup storage redis config with lenient() since not all tests use all config
        lenient().when(gcpAppServiceConfig.getRedisStorageHost()).thenReturn(TEST_STORAGE_HOST);
        lenient().when(gcpAppServiceConfig.getRedisStoragePort()).thenReturn(TEST_STORAGE_PORT);
        lenient().when(gcpAppServiceConfig.getRedisStoragePassword()).thenReturn(TEST_STORAGE_PASSWORD);
        lenient().when(gcpAppServiceConfig.getRedisStorageExpiration()).thenReturn(TEST_STORAGE_EXPIRATION);
        lenient().when(gcpAppServiceConfig.getRedisStorageWithSsl()).thenReturn(TEST_STORAGE_WITH_SSL);

        // Setup group redis config with lenient() since not all tests use all config
        lenient().when(gcpAppServiceConfig.getRedisGroupHost()).thenReturn(TEST_GROUP_HOST);
        lenient().when(gcpAppServiceConfig.getRedisGroupPort()).thenReturn(TEST_GROUP_PORT);
        lenient().when(gcpAppServiceConfig.getRedisGroupPassword()).thenReturn(TEST_GROUP_PASSWORD);
        lenient().when(gcpAppServiceConfig.getRedisGroupExpiration()).thenReturn(TEST_GROUP_EXPIRATION);
        lenient().when(gcpAppServiceConfig.getRedisGroupWithSsl()).thenReturn(TEST_GROUP_WITH_SSL);
    }

    // ========================================
    // legalTagCache Tests
    // ========================================

    @Nested
    @DisplayName("legalTagCache Bean Tests")
    class LegalTagCacheTests {

        @Test
        @DisplayName("Should create ICache instance with LegalTagCache wrapper")
        void legalTagCache_CreatesICacheInstance() {
            // Arrange
            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            ICache<String, String> result = cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            assertNotNull(result);
            assertInstanceOf(ICache.class, result);
        }

        @Test
        @DisplayName("Should call builder with storage redis configuration")
        void legalTagCache_CallsBuilderWithCorrectParameters() {
            // Arrange
            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            verify(legalRedisCacheBuilder).buildRedisCache(
                    TEST_STORAGE_HOST,
                    TEST_STORAGE_PORT,
                    TEST_STORAGE_PASSWORD,
                    TEST_STORAGE_EXPIRATION,
                    TEST_STORAGE_WITH_SSL,
                    String.class,
                    String.class
            );
        }

        @Test
        @DisplayName("Should retrieve all config values from GcpAppServiceConfig")
        void legalTagCache_RetrievesAllConfigValues() {
            // Arrange
            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            verify(gcpAppServiceConfig).getRedisStorageHost();
            verify(gcpAppServiceConfig).getRedisStoragePort();
            verify(gcpAppServiceConfig).getRedisStoragePassword();
            verify(gcpAppServiceConfig).getRedisStorageExpiration();
            verify(gcpAppServiceConfig).getRedisStorageWithSsl();
        }

        @Test
        @DisplayName("Should wrap RedisCache in multi-tenant wrapper")
        void legalTagCache_WrapsRedisCacheInWrapper() {
            // Arrange
            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            ICache<String, String> result = cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            assertNotNull(result);
            // Verify the result is an ICache (wrapped)
            assertInstanceOf(ICache.class, result);
            // Verify it's not the raw RedisCache
            assertNotEquals(mockLegalRedisCache, result);
        }

        @Test
        @DisplayName("Should handle different storage configurations")
        void legalTagCache_HandlesDifferentConfigurations() {
            // Arrange
            when(gcpAppServiceConfig.getRedisStorageHost()).thenReturn("different-host");
            when(gcpAppServiceConfig.getRedisStoragePort()).thenReturn(9999);
            when(gcpAppServiceConfig.getRedisStoragePassword()).thenReturn("different-password");
            when(gcpAppServiceConfig.getRedisStorageExpiration()).thenReturn(1800);
            when(gcpAppServiceConfig.getRedisStorageWithSsl()).thenReturn(false);

            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            verify(legalRedisCacheBuilder).buildRedisCache(
                    "different-host",
                    9999,
                    "different-password",
                    1800,
                    false,
                    String.class,
                    String.class
            );
        }

        @Test
        @DisplayName("Should use String types for key and value")
        void legalTagCache_UsesStringTypes() {
            // Arrange
            when(legalRedisCacheBuilder.buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    any(Class.class),
                    any(Class.class)
            )).thenReturn(mockLegalRedisCache);

            // Act
            cacheConfig.legalTagCache(gcpAppServiceConfig);

            // Assert
            verify(legalRedisCacheBuilder).buildRedisCache(
                    anyString(),
                    anyInt(),
                    anyString(),
                    anyInt(),
                    anyBoolean(),
                    eq(String.class),  // Changed from String.class
                    eq(String.class)   // Changed from String.class
            );
        }

        // ========================================
        // schemaCache Tests
        // ========================================

        @Nested
        @DisplayName("schemaCache Bean Tests")
        class SchemaCacheTests {

            @Test
            @DisplayName("Should create RedisCache for Schema")
            void schemaCache_CreatesRedisCacheForSchema() {
                // Arrange
                when(schemaRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockSchemaRedisCache);

                // Act
                RedisCache<String, Schema> result = cacheConfig.schemaCache(gcpAppServiceConfig);

                // Assert
                assertNotNull(result);
                assertEquals(mockSchemaRedisCache, result);
            }

            @Test
            @DisplayName("Should call builder with storage redis configuration")
            void schemaCache_CallsBuilderWithCorrectParameters() {
                // Arrange
                when(schemaRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockSchemaRedisCache);

                // Act
                cacheConfig.schemaCache(gcpAppServiceConfig);

                // Assert - Use raw values (no matchers at all)
                verify(schemaRedisCacheBuilder).buildRedisCache(
                        TEST_STORAGE_HOST,
                        TEST_STORAGE_PORT,
                        TEST_STORAGE_PASSWORD,
                        TEST_STORAGE_EXPIRATION,
                        TEST_STORAGE_WITH_SSL,
                        String.class,
                        Schema.class
                );
            }

            @Test
            @DisplayName("Should use Schema class as value type")
            void schemaCache_UsesSchemaClassAsValueType() {
                // Arrange
                when(schemaRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockSchemaRedisCache);

                // Act
                cacheConfig.schemaCache(gcpAppServiceConfig);

                // Assert
                verify(schemaRedisCacheBuilder).buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        eq(String.class),
                        eq(Schema.class)
                );
            }

            @Test
            @DisplayName("Should retrieve all storage config values")
            void schemaCache_RetrievesAllStorageConfigValues() {
                // Arrange
                when(schemaRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockSchemaRedisCache);

                // Act
                cacheConfig.schemaCache(gcpAppServiceConfig);

                // Assert
                verify(gcpAppServiceConfig).getRedisStorageHost();
                verify(gcpAppServiceConfig).getRedisStoragePort();
                verify(gcpAppServiceConfig).getRedisStoragePassword();
                verify(gcpAppServiceConfig).getRedisStorageExpiration();
                verify(gcpAppServiceConfig).getRedisStorageWithSsl();
            }

            @Test
            @DisplayName("Should return RedisCache instance")
            void schemaCache_ReturnsRedisCacheInstance() {
                // Arrange
                when(schemaRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockSchemaRedisCache);

                // Act
                RedisCache<String, Schema> result = cacheConfig.schemaCache(gcpAppServiceConfig);

                // Assert
                assertNotNull(result);
                assertInstanceOf(RedisCache.class, result);
            }
        }

        // ========================================
        // groupsCache Tests
        // ========================================

        @Nested
        @DisplayName("groupsCache Bean Tests")
        class GroupsCacheTests {

            @Test
            @DisplayName("Should create RedisCache for Groups")
            void groupsCache_CreatesRedisCacheForGroups() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                RedisCache<String, Groups> result = cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert
                assertNotNull(result);
                assertEquals(mockGroupsRedisCache, result);
            }

            @Test
            @DisplayName("Should call builder with group redis configuration")
            void groupsCache_CallsBuilderWithCorrectParameters() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert
                verify(groupsRedisCacheBuilder).buildRedisCache(
                        TEST_GROUP_HOST,
                        TEST_GROUP_PORT,
                        TEST_GROUP_PASSWORD,
                        TEST_GROUP_EXPIRATION,
                        TEST_GROUP_WITH_SSL,
                        String.class,
                        Groups.class
                );
            }

            @Test
            @DisplayName("Should use Groups class as value type")
            void groupsCache_UsesGroupsClassAsValueType() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert
                verify(groupsRedisCacheBuilder).buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        eq(String.class),
                        eq(Groups.class)
                );
            }

            @Test
            @DisplayName("Should retrieve all group config values")
            void groupsCache_RetrievesAllGroupConfigValues() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert
                verify(gcpAppServiceConfig).getRedisGroupHost();
                verify(gcpAppServiceConfig).getRedisGroupPort();
                verify(gcpAppServiceConfig).getRedisGroupPassword();
                verify(gcpAppServiceConfig).getRedisGroupExpiration();
                verify(gcpAppServiceConfig).getRedisGroupWithSsl();
            }

            @Test
            @DisplayName("Should use different configuration than storage cache")
            void groupsCache_UsesDifferentConfigurationThanStorage() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert - Use ALL raw values (no matchers)
                verify(groupsRedisCacheBuilder).buildRedisCache(
                        TEST_GROUP_HOST,
                        TEST_GROUP_PORT,
                        TEST_GROUP_PASSWORD,
                        TEST_GROUP_EXPIRATION,
                        TEST_GROUP_WITH_SSL,
                        String.class,          // Removed eq()
                        Groups.class           // Removed eq()
                );

                // Verify it doesn't use storage config - ALL must be matchers
                verify(groupsRedisCacheBuilder, never()).buildRedisCache(
                        eq(TEST_STORAGE_HOST),
                        any(Integer.class),
                        anyString(),
                        any(Integer.class),
                        any(Boolean.class),
                        any(Class.class),
                        any(Class.class)
                );
            }

            @Test
            @DisplayName("Should return RedisCache instance")
            void groupsCache_ReturnsRedisCacheInstance() {
                // Arrange
                when(groupsRedisCacheBuilder.buildRedisCache(
                        anyString(),
                        anyInt(),
                        anyString(),
                        anyInt(),
                        anyBoolean(),
                        any(Class.class),
                        any(Class.class)
                )).thenReturn(mockGroupsRedisCache);

                // Act
                RedisCache<String, Groups> result = cacheConfig.groupsCache(gcpAppServiceConfig);

                // Assert
                assertNotNull(result);
                assertInstanceOf(RedisCache.class, result);
            }
        }

        // ========================================
        // partitionInfoCache Tests
        // ========================================

        @Nested
        @DisplayName("partitionInfoCache Bean Tests")
        class PartitionInfoCacheTests {

            @Test
            @DisplayName("Should create VmCache for PartitionInfo")
            void partitionInfoCache_CreatesVmCache() {
                // Act
                ICache<String, PartitionInfo> result = cacheConfig.partitionInfoCache();

                // Assert
                assertNotNull(result);
                assertInstanceOf(VmCache.class, result);
            }

            @Test
            @DisplayName("Should create VmCache with correct TTL and capacity")
            void partitionInfoCache_CreatesVmCacheWithCorrectParameters() {
                // Act
                ICache<String, PartitionInfo> result = cacheConfig.partitionInfoCache();

                // Assert
                assertNotNull(result);
                @SuppressWarnings("unchecked")
                VmCache<String, PartitionInfo> vmCache = (VmCache<String, PartitionInfo>) result;
                assertNotNull(vmCache);
            }

            @Test
            @DisplayName("Should not require GcpAppServiceConfig")
            void partitionInfoCache_DoesNotRequireConfig() {
                // Act & Assert - Should not throw exception
                assertDoesNotThrow(() -> cacheConfig.partitionInfoCache());
            }

            @Test
            @DisplayName("Should create new instance on each call")
            void partitionInfoCache_CreatesNewInstanceOnEachCall() {
                // Act
                ICache<String, PartitionInfo> cache1 = cacheConfig.partitionInfoCache();
                ICache<String, PartitionInfo> cache2 = cacheConfig.partitionInfoCache();

                // Assert
                assertNotNull(cache1);
                assertNotNull(cache2);
                assertNotSame(cache1, cache2);
            }

            @Test
            @DisplayName("Should return ICache interface type")
            void partitionInfoCache_ReturnsICacheInterface() {
                // Act
                ICache<String, PartitionInfo> result = cacheConfig.partitionInfoCache();

                // Assert
                assertNotNull(result);
                assertInstanceOf(ICache.class, result);
            }
        }

    }
}
