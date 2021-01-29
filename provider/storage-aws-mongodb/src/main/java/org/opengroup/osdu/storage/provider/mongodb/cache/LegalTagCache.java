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

package org.opengroup.osdu.storage.provider.mongodb.cache;

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class LegalTagCache implements ICache<String, String> {

    @Inject
    private TenantInfo tenant;

    private final MultiTenantCache<String> caches;

    public LegalTagCache(@Value("${aws.elasticache.cluster.endpoint}") final String REDIS_LEGALTAG_HOST, @Value("${aws.elasticache.cluster.port}") final String REDIS_LEGALTAG_PORT) {
        this.caches = new MultiTenantCache<>(new RedisCache<>(REDIS_LEGALTAG_HOST,Integer.parseInt(REDIS_LEGALTAG_PORT),
                60 * 60,
                String.class,
                String.class));
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
        return this.caches.get(String.format("%s:legalTag", this.tenant));
    }

}
