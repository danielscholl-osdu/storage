/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Autowired;

public class LegalTagMultiTenantCache implements ICache<String, String> {

    @Autowired
    private TenantInfo tenant;

    private final MultiTenantCache<String> caches;

    public LegalTagMultiTenantCache(RedisCache<String, String> redisCache) {
        this.caches = new MultiTenantCache<>(redisCache);
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
