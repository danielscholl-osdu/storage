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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
@DisplayName("LegalTagMultiTenantCache Tests")
class LegalTagMultiTenantCacheTest {

    @Mock
    private RedisCache<String, String> redisCache;

    @Mock
    private TenantInfo tenantInfo;

    @Mock
    private ICache<String, String> mockPartitionCache;

    @Mock
    private MultiTenantCache<String> multiTenantCache;

    private LegalTagMultiTenantCache legalTagCache;

    private static final String TEST_TENANT_NAME = "test-tenant";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-value";

    @BeforeEach
    void setUp() throws Exception {
        legalTagCache = new LegalTagMultiTenantCache(redisCache);

        // Inject mock TenantInfo using reflection
        Field tenantField = LegalTagMultiTenantCache.class.getDeclaredField("tenant");
        tenantField.setAccessible(true);
        tenantField.set(legalTagCache, tenantInfo);

        // Setup default tenant behavior with lenient() since not all tests use it
        lenient().when(tenantInfo.toString()).thenReturn(TEST_TENANT_NAME);
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with RedisCache")
        void shouldCreateInstanceWithRedisCache() {
            // Act
            LegalTagMultiTenantCache cache = new LegalTagMultiTenantCache(redisCache);

            // Assert
            assertNotNull(cache);
        }

        @Test
        @DisplayName("Should initialize MultiTenantCache with RedisCache")
        void shouldInitializeMultiTenantCacheWithRedisCache() throws Exception {
            // Act
            LegalTagMultiTenantCache cache = new LegalTagMultiTenantCache(redisCache);

            // Assert - Use reflection to verify caches field
            Field cachesField = LegalTagMultiTenantCache.class.getDeclaredField("caches");
            cachesField.setAccessible(true);
            MultiTenantCache<String> caches = (MultiTenantCache<String>) cachesField.get(cache);
            assertNotNull(caches);
        }

        @Test
        @DisplayName("Should handle null RedisCache")
        void shouldHandleNullRedisCache() {
            // Act & Assert - Constructor should not throw, but will fail later
            assertDoesNotThrow(() -> new LegalTagMultiTenantCache(null));
        }

        @Test
        @DisplayName("Should create multiple independent instances")
        void shouldCreateMultipleIndependentInstances() {
            // Act
            LegalTagMultiTenantCache cache1 = new LegalTagMultiTenantCache(redisCache);
            LegalTagMultiTenantCache cache2 = new LegalTagMultiTenantCache(redisCache);

            // Assert
            assertNotNull(cache1);
            assertNotNull(cache2);
            assertNotSame(cache1, cache2);
        }
    }

    // ========================================
    // put() Method Tests
    // ========================================

    @Nested
    @DisplayName("put() Method Tests")
    class PutMethodTests {

        @BeforeEach
        void setUp() throws Exception {
            setupMultiTenantCacheMock();
        }

        @Test
        @DisplayName("Should put value to partition cache")
        void shouldPutValueToPartitionCache() {
            // Act
            legalTagCache.put(TEST_KEY, TEST_VALUE);

            // Assert
            verify(mockPartitionCache).put(TEST_KEY, TEST_VALUE);
        }

        @Test
        @DisplayName("Should use tenant-based key for cache retrieval")
        void shouldUseTenantBasedKeyForCacheRetrieval() {
            // Act
            legalTagCache.put(TEST_KEY, TEST_VALUE);

            // Assert - Verify multiTenantCache.get was called with tenant:legalTag format
            verify(multiTenantCache).get(TEST_TENANT_NAME + ":legalTag");
        }

        @Test
        @DisplayName("Should put null value")
        void shouldPutNullValue() {
            // Act
            legalTagCache.put(TEST_KEY, null);

            // Assert
            verify(mockPartitionCache).put(TEST_KEY, null);
        }

        @Test
        @DisplayName("Should put value with null key")
        void shouldPutValueWithNullKey() {
            // Act
            legalTagCache.put(null, TEST_VALUE);

            // Assert
            verify(mockPartitionCache).put(null, TEST_VALUE);
        }

        @Test
        @DisplayName("Should put empty string value")
        void shouldPutEmptyStringValue() {
            // Act
            legalTagCache.put(TEST_KEY, "");

            // Assert
            verify(mockPartitionCache).put(TEST_KEY, "");
        }

        @Test
        @DisplayName("Should handle multiple put operations")
        void shouldHandleMultiplePutOperations() {
            // Act
            legalTagCache.put("key1", "value1");
            legalTagCache.put("key2", "value2");
            legalTagCache.put("key3", "value3");

            // Assert
            verify(mockPartitionCache, times(3)).put(anyString(), anyString());
        }
    }

    // ========================================
    // get() Method Tests
    // ========================================

    @Nested
    @DisplayName("get() Method Tests")
    class GetMethodTests {

        @BeforeEach
        void setUp() throws Exception {
            setupMultiTenantCacheMock();
        }

        @Test
        @DisplayName("Should get value from partition cache")
        void shouldGetValueFromPartitionCache() {
            // Arrange
            when(mockPartitionCache.get(TEST_KEY)).thenReturn(TEST_VALUE);

            // Act
            String result = legalTagCache.get(TEST_KEY);

            // Assert
            assertEquals(TEST_VALUE, result);
            verify(mockPartitionCache).get(TEST_KEY);
        }

        @Test
        @DisplayName("Should return null when key not found")
        void shouldReturnNullWhenKeyNotFound() {
            // Arrange
            when(mockPartitionCache.get(TEST_KEY)).thenReturn(null);

            // Act
            String result = legalTagCache.get(TEST_KEY);

            // Assert
            assertNull(result);
            verify(mockPartitionCache).get(TEST_KEY);
        }

        @Test
        @DisplayName("Should get value with null key")
        void shouldGetValueWithNullKey() {
            // Arrange
            when(mockPartitionCache.get(null)).thenReturn(null);

            // Act
            String result = legalTagCache.get(null);

            // Assert
            assertNull(result);
            verify(mockPartitionCache).get(null);
        }

        @Test
        @DisplayName("Should get empty string value")
        void shouldGetEmptyStringValue() {
            // Arrange
            when(mockPartitionCache.get(TEST_KEY)).thenReturn("");

            // Act
            String result = legalTagCache.get(TEST_KEY);

            // Assert
            assertEquals("", result);
        }

        @Test
        @DisplayName("Should handle multiple get operations")
        void shouldHandleMultipleGetOperations() {
            // Arrange
            when(mockPartitionCache.get("key1")).thenReturn("value1");
            when(mockPartitionCache.get("key2")).thenReturn("value2");
            when(mockPartitionCache.get("key3")).thenReturn("value3");

            // Act
            String result1 = legalTagCache.get("key1");
            String result2 = legalTagCache.get("key2");
            String result3 = legalTagCache.get("key3");

            // Assert
            assertEquals("value1", result1);
            assertEquals("value2", result2);
            assertEquals("value3", result3);
            verify(mockPartitionCache, times(3)).get(anyString());
        }
    }

    // ========================================
    // delete() Method Tests
    // ========================================

    @Nested
    @DisplayName("delete() Method Tests")
    class DeleteMethodTests {

        @BeforeEach
        void setUp() throws Exception {
            setupMultiTenantCacheMock();
        }

        @Test
        @DisplayName("Should delete key from partition cache")
        void shouldDeleteKeyFromPartitionCache() {
            // Act
            legalTagCache.delete(TEST_KEY);

            // Assert
            verify(mockPartitionCache).delete(TEST_KEY);
        }

        @Test
        @DisplayName("Should delete with null key")
        void shouldDeleteWithNullKey() {
            // Act
            legalTagCache.delete(null);

            // Assert
            verify(mockPartitionCache).delete(null);
        }

        @Test
        @DisplayName("Should handle deleting non-existent key")
        void shouldHandleDeletingNonExistentKey() {
            // Arrange
            doNothing().when(mockPartitionCache).delete(TEST_KEY);

            // Act & Assert
            assertDoesNotThrow(() -> legalTagCache.delete(TEST_KEY));
            verify(mockPartitionCache).delete(TEST_KEY);
        }

        @Test
        @DisplayName("Should handle multiple delete operations")
        void shouldHandleMultipleDeleteOperations() {
            // Act
            legalTagCache.delete("key1");
            legalTagCache.delete("key2");
            legalTagCache.delete("key3");

            // Assert
            verify(mockPartitionCache, times(3)).delete(anyString());
        }
    }

    // ========================================
    // clearAll() Method Tests
    // ========================================

    @Nested
    @DisplayName("clearAll() Method Tests")
    class ClearAllMethodTests {

        @BeforeEach
        void setUp() throws Exception {
            setupMultiTenantCacheMock();
        }

        @Test
        @DisplayName("Should clear all from partition cache")
        void shouldClearAllFromPartitionCache() {
            // Act
            legalTagCache.clearAll();

            // Assert
            verify(mockPartitionCache).clearAll();
        }

        @Test
        @DisplayName("Should handle clearAll when cache is empty")
        void shouldHandleClearAllWhenCacheIsEmpty() {
            // Arrange
            doNothing().when(mockPartitionCache).clearAll();

            // Act & Assert
            assertDoesNotThrow(() -> legalTagCache.clearAll());
            verify(mockPartitionCache).clearAll();
        }

        @Test
        @DisplayName("Should handle multiple clearAll operations")
        void shouldHandleMultipleClearAllOperations() {
            // Act
            legalTagCache.clearAll();
            legalTagCache.clearAll();
            legalTagCache.clearAll();

            // Assert
            verify(mockPartitionCache, times(3)).clearAll();
        }
    }

    // ========================================
    // Tenant Isolation Tests
    // ========================================

    @Nested
    @DisplayName("Tenant Isolation Tests")
    class TenantIsolationTests {

        @BeforeEach
        void setUp() throws Exception {
            setupMultiTenantCacheMock();
        }

        @Test
        @DisplayName("Should use tenant-specific cache key format")
        void shouldUseTenantSpecificCacheKeyFormat() {
            // Act
            legalTagCache.put(TEST_KEY, TEST_VALUE);

            // Assert - Verify the multiTenantCache.get was called with proper format
            verify(multiTenantCache).get(TEST_TENANT_NAME + ":legalTag");
        }

        @Test
        @DisplayName("Should isolate operations by tenant")
        void shouldIsolateOperationsByTenant() {
            // Act
            legalTagCache.put(TEST_KEY, TEST_VALUE);
            legalTagCache.get(TEST_KEY);
            legalTagCache.delete(TEST_KEY);

            // Assert - Verify multiTenantCache.get was called for each operation
            verify(multiTenantCache, times(3)).get(anyString());
        }

        @Test
        @DisplayName("Should format cache key with tenant and legalTag suffix")
        void shouldFormatCacheKeyWithTenantAndLegalTagSuffix() {
            // Arrange
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

            // Act
            legalTagCache.get(TEST_KEY);

            // Assert - Verify the format structure
            verify(multiTenantCache).get(keyCaptor.capture());
            String capturedKey = keyCaptor.getValue();

            assertTrue(capturedKey.startsWith(TEST_TENANT_NAME),
                    "Key should start with tenant name");
            assertTrue(capturedKey.endsWith(":legalTag"),
                    "Key should end with :legalTag suffix");
            assertEquals(TEST_TENANT_NAME + ":legalTag", capturedKey,
                    "Key should follow tenant:legalTag format");
        }
    }

    // ========================================
    // Field Annotation Tests
    // ========================================

    @Nested
    @DisplayName("Field Annotation Tests")
    class FieldAnnotationTests {

        @Test
        @DisplayName("Should have @Autowired on tenant field")
        void shouldHaveAutowiredOnTenantField() throws NoSuchFieldException {
            // Act
            Field tenantField = LegalTagMultiTenantCache.class.getDeclaredField("tenant");

            // Assert
            assertNotNull(tenantField);
            assertTrue(tenantField.isAnnotationPresent(Autowired.class));
        }

        @Test
        @DisplayName("Tenant field should be private")
        void tenantFieldShouldBePrivate() throws NoSuchFieldException {
            // Act
            Field tenantField = LegalTagMultiTenantCache.class.getDeclaredField("tenant");

            // Assert
            assertTrue(java.lang.reflect.Modifier.isPrivate(tenantField.getModifiers()));
        }

        @Test
        @DisplayName("Caches field should be private final")
        void cachesFieldShouldBePrivateFinal() throws NoSuchFieldException {
            // Act
            Field cachesField = LegalTagMultiTenantCache.class.getDeclaredField("caches");

            // Assert
            assertTrue(java.lang.reflect.Modifier.isPrivate(cachesField.getModifiers()));
            assertTrue(java.lang.reflect.Modifier.isFinal(cachesField.getModifiers()));
        }
    }

    // ========================================
    // Interface Implementation Tests
    // ========================================

    @Nested
    @DisplayName("Interface Implementation Tests")
    class InterfaceImplementationTests {

        @Test
        @DisplayName("Should implement ICache interface")
        void shouldImplementICacheInterface() {
            // Assert
            assertTrue(ICache.class.isAssignableFrom(LegalTagMultiTenantCache.class));
        }

        @Test
        @DisplayName("Should implement all ICache methods")
        void shouldImplementAllICacheMethods() throws NoSuchMethodException {
            // Assert
            assertNotNull(LegalTagMultiTenantCache.class.getMethod("put", String.class, String.class));
            assertNotNull(LegalTagMultiTenantCache.class.getMethod("get", String.class));
            assertNotNull(LegalTagMultiTenantCache.class.getMethod("delete", String.class));
            assertNotNull(LegalTagMultiTenantCache.class.getMethod("clearAll"));
        }

        @Test
        @DisplayName("Should have generic types String, String")
        void shouldHaveGenericTypesStringString() {
            // This is verified by compilation, but we can check the interface
            assertTrue(ICache.class.isAssignableFrom(LegalTagMultiTenantCache.class));
        }
    }

    // ========================================
    // Class Structure Tests
    // ========================================

    @Nested
    @DisplayName("Class Structure Tests")
    class ClassStructureTests {

        @Test
        @DisplayName("Class should be public")
        void classShouldBePublic() {
            assertTrue(java.lang.reflect.Modifier.isPublic(
                    LegalTagMultiTenantCache.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should not be final")
        void classShouldNotBeFinal() {
            assertFalse(java.lang.reflect.Modifier.isFinal(
                    LegalTagMultiTenantCache.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should have exactly 2 fields")
        void classShouldHaveExactlyTwoFields() {
            assertEquals(2, LegalTagMultiTenantCache.class.getDeclaredFields().length);
        }

        @Test
        @DisplayName("Class should have one constructor")
        void classShouldHaveOneConstructor() {
            assertEquals(1, LegalTagMultiTenantCache.class.getDeclaredConstructors().length);
        }

        @Test
        @DisplayName("Should be in correct package")
        void shouldBeInCorrectPackage() {
            assertEquals("org.opengroup.osdu.storage.provider.gcp.web.cache",
                    LegalTagMultiTenantCache.class.getPackageName());
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void setupMultiTenantCacheMock() throws Exception {
        // Replace the caches field with a mock MultiTenantCache
        Field cachesField = LegalTagMultiTenantCache.class.getDeclaredField("caches");
        cachesField.setAccessible(true);
        cachesField.set(legalTagCache, multiTenantCache);

        // Mock the get method to return our mock partition cache
        when(multiTenantCache.get(anyString())).thenReturn(mockPartitionCache);
    }
}
