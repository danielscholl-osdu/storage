// Copyright © 2020 Amazon Web Services
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

import org.opengroup.osdu.core.aws.cache.CacheParameters;
import org.opengroup.osdu.core.aws.cache.NameSpacedCache;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.inject.Inject;

@Component("LegalTagCache")
public class LegalTagCache implements ICache<String, String> {
    @Inject
    private TenantInfo tenant;

    private final MultiTenantCache<String> caches;
    static final int EXP_TIME_SECONDS = 60 * 60;
    static final int MAX_CACHE_SIZE = 10;
    static final String KEY_NAMESPACE = "legalTags";

    // overloaded constructor for testing
    public LegalTagCache(
        @Value("${aws.elasticache.cluster.endpoint:null}") String redisEndpoint,
        @Value("${aws.elasticache.cluster.port:null}") String redisPort,
        @Value("${aws.elasticache.cluster.key:null}") String redisPassword
    ) {
        CacheParameters<String, String> cacheParameters = CacheParameters.<String, String>builder()
                                                                         .expTimeSeconds(EXP_TIME_SECONDS)
                                                                         .maxSize(MAX_CACHE_SIZE)
                                                                         .defaultHost(redisEndpoint)
                                                                         .defaultPort(redisPort)
                                                                         .defaultPassword(redisPassword)
                                                                         .keyNamespace(KEY_NAMESPACE)
                                                                         .build()
                                                                         .initFromLocalParameters(String.class, String.class);
        NameSpacedCache<String> internalCache = new NameSpacedCache<>(cacheParameters);
        caches = new MultiTenantCache<>(internalCache);
    }

    public LegalTagCache() {
        this(null, null, null);
    }

    @Override
    public void put(String key, String val) {
        this.partitionCache().put(key, val);
    }

    @Override
    public String get(String key) {
        return this.partitionCache().get(key);
    }

    @Override
    public void delete(String key) {
         this.partitionCache().delete(key);
    }

    @Override
    public void clearAll() {
        this.partitionCache().clearAll();
    }

    private ICache<String, String> partitionCache() {
        return this.caches.get(String.format("%s:legalTag", this.tenant.toString()));
    }

}
