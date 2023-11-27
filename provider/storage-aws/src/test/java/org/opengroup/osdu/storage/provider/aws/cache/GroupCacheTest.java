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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.aws.cache.CacheFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;

import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
@TestPropertySource(properties = {"aws.elasticache.cluster.endpoint=testHost", "aws.elasticache.cluster.port=1234", "aws.elasticache.cluster.key=testKey"})
class GroupCacheTest {
    
    private GroupCache<String, String> groupCache;

    @Mock
    private K8sLocalParameterProvider provider;

    @Mock
    private ICache cache;

    @Mock
    private CacheFactory cacheFactory;

    @Mock
    private RedisCache redisCache;

    @Mock
    private VmCache vmCache;

    @Mock
    private DpsHeaders headers;

    private String dataPartitionId = "testPartitionId";

    private String authorization = "testAuthorization";

    String REDIS_SEARCH_HOST = "testHost";
    String REDIS_SEARCH_PORT = "1234";
    String REDIS_SEARCH_KEY = "testKey";

    @BeforeEach
    void setUp() throws K8sParameterNotFoundException, JsonProcessingException {
        MockitoAnnotations.openMocks(this);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", null)).thenReturn(REDIS_SEARCH_HOST);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", null)).thenReturn(REDIS_SEARCH_PORT);
        when(provider.getLocalMode()).thenReturn(true);
        when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartitionId);
        when(headers.getAuthorization()).thenReturn(authorization);
    }

    @Test
    void testConstructor_WhenLocalModeAndCacheNotDisabled_ShouldUseVmCache()
            throws JsonProcessingException, K8sParameterNotFoundException {
        when(cacheFactory.getVmCache(60, 10)).thenReturn(vmCache);
        groupCache = new GroupCache("false", provider, REDIS_SEARCH_KEY, cacheFactory);
        
        ICache realCache = groupCache.getCacheForTesting();
        assertTrue(realCache instanceof VmCache);
    }

    @Test 
    void testConstructor_WhenNotLocalModeAndNotCacheDisabled_ShouldUseRedisCacheWithNullCredentials()
            throws JsonProcessingException, K8sParameterNotFoundException {
        when(provider.getCredentialsAsMap("CACHE_CLUSTER_KEY")).thenReturn(null);
        when(provider.getLocalMode()).thenReturn(false);
        when(cacheFactory.getRedisCache(REDIS_SEARCH_HOST, Integer.parseInt(REDIS_SEARCH_PORT), REDIS_SEARCH_KEY, 60, String.class, Groups.class)).thenReturn(redisCache);
        groupCache = new GroupCache<String, String>("false", provider, REDIS_SEARCH_KEY, cacheFactory);

        ICache realCache = groupCache.getCacheForTesting();
        assertTrue(realCache instanceof RedisCache);
    }
    

    @Test 
    void testConstructor_WhenNotLocalModeAndNotCacheDisabled_ShouldUseRedisCacheWithCredentials()
            throws JsonProcessingException, K8sParameterNotFoundException, NoSuchFieldException, IllegalAccessException {
        Map<String, String> credentialsMap = new HashMap<>();
        credentialsMap.put("token", "testToken");
        when(provider.getCredentialsAsMap("CACHE_CLUSTER_KEY")).thenReturn(credentialsMap);
        when(provider.getLocalMode()).thenReturn(false);
        when(cacheFactory.getRedisCache(REDIS_SEARCH_HOST, Integer.parseInt(REDIS_SEARCH_PORT), credentialsMap.get("token"), 60, String.class, Groups.class)).thenReturn(redisCache);
        groupCache = new GroupCache<String, String>("false", provider, REDIS_SEARCH_KEY, cacheFactory);

        ICache realCache = groupCache.getCacheForTesting();
        assertTrue(realCache instanceof RedisCache);
    }

    @Test
    void testPut() throws JsonProcessingException, K8sParameterNotFoundException{
        String key = "key";
        String value = "value";
        groupCache = new GroupCache<>("false", provider, REDIS_SEARCH_KEY, cacheFactory);
        ReflectionTestUtils.setField(groupCache, "cache", cache);
        groupCache.put(key, value);

        verify(cache, times(1)).put(key, value);
    }

    @Test
    void testGet() throws JsonProcessingException, K8sParameterNotFoundException{
        String key = "key";
        groupCache = new GroupCache<>("false", provider, REDIS_SEARCH_KEY, cacheFactory);
        ReflectionTestUtils.setField(groupCache, "cache", cache);
        groupCache.get(key);
        

        verify(cache, times(1)).get(key);
    }

    @Test
    void testDelete() throws JsonProcessingException, K8sParameterNotFoundException{
        String key = "key";
        groupCache = new GroupCache<>("false", provider, REDIS_SEARCH_KEY, cacheFactory);
        ReflectionTestUtils.setField(groupCache, "cache", cache);
        groupCache.delete(key);

        verify(cache, times(1)).delete(key);
    }

    @Test
    void testClearAll() throws JsonProcessingException, K8sParameterNotFoundException{
        when(provider.getLocalMode()).thenReturn(true);
        groupCache = new GroupCache<>("false", provider, REDIS_SEARCH_KEY, cacheFactory);
        ReflectionTestUtils.setField(groupCache, "cache", cache);
        groupCache.clearAll();

        verify(cache, times(1)).clearAll();
    }

    @Test
    void testGetGroupCacheKey() {
        // Act
        String result = groupCache.getGroupCacheKey(headers);

        // Assert
        String expectedKey = String.format("entitlement-groups:%s:%s", dataPartitionId, authorization);
        assertEquals(Crc32c.hashToBase64EncodedString(expectedKey), result);
    }

    @Test
    void testGetPartitionGroupsCacheKey() {
        // Act
        String result = groupCache.getPartitionGroupsCacheKey(dataPartitionId);

        // Assert
        String expectedKey = String.format("entitlement-groups:data-partition:%s", dataPartitionId);
        assertEquals(Crc32c.hashToBase64EncodedString(expectedKey), result);
    }
}
