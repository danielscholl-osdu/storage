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

import org.opengroup.osdu.core.aws.cache.CacheParameters;
import org.opengroup.osdu.core.aws.cache.DefaultCache;
import org.opengroup.osdu.core.aws.cache.NameSpacedCache;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class GroupCache<K,V> extends DefaultCache<K, V> {
    static final String KEY_NAMESPACE = "groupCache";
    private static <K, V> ICache<K, V> createInternalCache(String redisEndpoint, String redisPort, String redisPassword) {
        CacheParameters<String, V> cacheParams = CacheParameters.<String, V>builder()
                                                                .expTimeSeconds(300)
                                                                .maxSize(1000)
                                                                .defaultHost(redisEndpoint)
                                                                .defaultPort(redisPort)
                                                                .defaultPassword(redisPassword)
                                                                .keyNamespace(KEY_NAMESPACE)
                                                                .build()
                                                                .initFromLocalParameters(String.class, (Class<V>) Groups.class);
        return (ICache<K, V>) new NameSpacedCache<>(cacheParams);
    }

    public GroupCache(
        @Value("${aws.elasticache.cluster.endpoint:null}") String redisEndpoint,
        @Value("${aws.elasticache.cluster.port:null}") String redisPort,
        @Value("${aws.elasticache.cluster.key:null}") String redisPassword
    ) {
        super(createInternalCache(redisEndpoint, redisPort, redisPassword));
    }

    public GroupCache() {
        this(null, null, null);
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
}
