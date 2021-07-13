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

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupCache extends RedisCache<String, Groups> {
    public GroupCache(@Value("${aws.elasticache.cluster.endpoint}") final String REDIS_GROUP_HOST, @Value("${aws.elasticache.cluster.port}") final String REDIS_GROUP_PORT, @Value("${aws.elasticache.cluster.key}") final String REDIS_GROUP_KEY) {
        super(REDIS_GROUP_HOST, Integer.parseInt(REDIS_GROUP_PORT), REDIS_GROUP_KEY, 60, String.class, Groups.class);
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
