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
import org.opengroup.osdu.core.aws.cache.DummyCache;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.aws.cache.CacheFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GroupCache<K,V> implements ICache<K,V>{
    @Value("${aws.elasticache.cluster.endpoint:null}")
    String REDIS_SEARCH_HOST;
    @Value("${aws.elasticache.cluster.port:null}")
    String REDIS_SEARCH_PORT;
    @Value("${aws.elasticache.cluster.key:null}")
    String REDIS_SEARCH_KEY;

    private ICache<K,V> cache;
    
    private K8sLocalParameterProvider provider;

    private CacheFactory<K,V> cacheFactory;

    // overloaded constructor for testing
    public GroupCache() throws K8sParameterNotFoundException, JsonProcessingException {
        this(System.getenv("DISABLE_CACHE"), null, null, null);
    }


    public GroupCache(String disableCacheResult, K8sLocalParameterProvider givenProvider, String REDIS_SEARCH_KEY, CacheFactory<K,V> cacheFactory) throws K8sParameterNotFoundException, JsonProcessingException {
        if (cacheFactory != null) {
            this.cacheFactory = cacheFactory;
        } else {
            this.cacheFactory = new CacheFactory<>();
        }
        if (givenProvider == null){
            this.provider = new K8sLocalParameterProvider();
        }
        else{
            this.provider = givenProvider;
        }
        if (REDIS_SEARCH_KEY != null){
            this.REDIS_SEARCH_KEY = REDIS_SEARCH_KEY;
        }
        if (provider.getLocalMode()){
            if (Boolean.parseBoolean(disableCacheResult)){
                this.cache =  new DummyCache();
            }
            this.cache =  this.cacheFactory.getVmCache(60, 10);

        } else {
            String host = provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", REDIS_SEARCH_HOST);
            int port = Integer.parseInt(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", REDIS_SEARCH_PORT));
            Map<String, String > credential =provider.getCredentialsAsMap("CACHE_CLUSTER_KEY");
            String password;
            if (credential !=null){
                password = credential.get("token");
            } else{
                password = REDIS_SEARCH_KEY;
            }
            this.cache =  this.cacheFactory.getRedisCache(host, port, password, 60, (Class<K>) String.class, (Class<V>) Groups.class);

        }
    }

    public static String getGroupCacheKey(DpsHeaders headers) {
    String key = String.format("entitlement-groups:%s:%s", headers.getPartitionIdWithFallbackToAccountId(),
            headers.getAuthorization());
    return Crc32c.hashToBase64EncodedString(key);
    }
    public static String getPartitionGroupsCacheKey(String dataPartitionId) {
        String key = String.format("entitlement-groups:data-partition:%s", dataPartitionId);
        return Crc32c.hashToBase64EncodedString(key);
    }

    ICache<K,V> getCacheForTesting(){
        return this.cache;
    }

    @Override
    public void put(K k, V o) {
        this.cache.put(k,o);
    }

    @Override
    public V get(K k) {
        return (V) this.cache.get(k);
    }

    @Override
    public void delete(K k) {
        this.cache.delete(k);
    }

    @Override
    public void clearAll() {
        this.cache.clearAll();
    }
}