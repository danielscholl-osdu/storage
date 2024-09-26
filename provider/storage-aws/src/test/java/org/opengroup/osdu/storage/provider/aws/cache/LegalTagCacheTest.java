// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.storage.provider.aws.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opengroup.osdu.core.aws.cache.CacheParameters;
import org.opengroup.osdu.core.aws.cache.DefaultCache;
import org.opengroup.osdu.core.aws.cache.NameSpacedCache;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.test.util.ReflectionTestUtils;

class LegalTagCacheTest {

    @Mock
    private MultiTenantCache<String> caches;

    @Mock
    private ICache<String, String> partitionCache;

    @Mock
    private K8sLocalParameterProvider provider;

    private MockedConstruction<NameSpacedCache> nameSpacedCache;
    private MockedConstruction<MultiTenantCache> multiTenantCache;

    private final String testKey = "testKey";
    private final String testVal = "testVal";
    private final String tenantStringVal = "tenant1";
    private final String tenantCacheKey = String.format("%s:legalTag", tenantStringVal);
    @Mock
    private TenantInfo tenant;

    private LegalTagCache legalTagCache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(tenant.toString()).thenReturn(tenantStringVal);
        when(provider.getLocalMode()).thenReturn(true);
        when(caches.get(anyString())).thenReturn(partitionCache);
        nameSpacedCache = Mockito.mockConstruction(NameSpacedCache.class, (mock, context) -> {
            List<?> args = context.arguments();
            assertEquals(1, context.arguments().size());
            CacheParameters<String, String> cacheParameter = (CacheParameters<String, String>) context.arguments().get(0);
            assertEquals(LegalTagCache.EXP_TIME_SECONDS, cacheParameter.getExpTimeSeconds());
            assertEquals(LegalTagCache.MAX_CACHE_SIZE, cacheParameter.getMaxSize());
            assertEquals(LegalTagCache.KEY_NAMESPACE, cacheParameter.getKeyNamespace());
        });
        multiTenantCache = Mockito.mockConstruction(MultiTenantCache.class, (mock, context) -> {
            List<?> args = context.arguments();
            assertEquals(1, context.arguments().size());
            assertTrue(args.get(0) instanceof NameSpacedCache);
            when(mock.get(tenantCacheKey)).thenReturn(partitionCache);
        });
        legalTagCache = new LegalTagCache();
        ReflectionTestUtils.setField(legalTagCache, "tenant", tenant);
    }

    @AfterEach
    void teardown() {
        multiTenantCache.close();
        nameSpacedCache.close();
    }

    @Test
    void put_shouldPutValueIntoPartitionCache() {
        legalTagCache.put(testKey, testVal);
        verify(partitionCache, times(1)).put(testKey, testVal);
    }

    @Test
    void get_shouldGetValueFromPartitionCache() {
        legalTagCache.get(testKey);

        verify(partitionCache, times(1)).get(testKey);
    }

    @Test
    void delete_shouldDeleteKeyFromPartitionCache() {
        legalTagCache.delete(testKey);

        verify(partitionCache, times(1)).delete(testKey);
    }

    @Test
    void clearAll_shouldClearAllEntriesInPartitionCache() {
        legalTagCache.clearAll();

        verify(partitionCache, times(1)).clearAll();
    }
}
