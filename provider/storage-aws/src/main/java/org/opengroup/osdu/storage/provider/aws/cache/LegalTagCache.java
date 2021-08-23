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

import com.fasterxml.jackson.core.JsonProcessingException;
import jdk.nashorn.internal.runtime.regexp.joni.ast.StringNode;
import org.opengroup.osdu.core.aws.cache.DummyCache;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.Map;

@Component
public class LegalTagCache implements ICache<String, String> {
    @Value("${aws.elasticache.cluster.endpoint}")
    String REDIS_SEARCH_HOST;
    @Value("${aws.elasticache.cluster.port}")
    String REDIS_SEARCH_PORT;
    @Value("${aws.elasticache.cluster.key}")
    String REDIS_SEARCH_KEY;
    @Inject
    private TenantInfo tenant;

    private final MultiTenantCache<String> caches;
    private boolean local;
    public LegalTagCache() throws K8sParameterNotFoundException, JsonProcessingException {
        int expTimeSeconds = 60 * 60;
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        if (provider.getLocalMode()){
            if (Boolean.parseBoolean(System.getenv("DISABLE_CACHE"))){
                caches =  new MultiTenantCache<String>(new DummyCache<>());
            }else{
                caches = new MultiTenantCache<String>(new VmCache<String,String>(expTimeSeconds, 10));
            }

        }else {
            String host = provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", REDIS_SEARCH_HOST);
            int port = Integer.parseInt(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", REDIS_SEARCH_PORT));
            Map<String, String > credential =provider.getCredentialsAsMap("CACHE_CLUSTER_KEY");
            String password;
            if (credential !=null){
                password = credential.get("token");
            }else{
                password = REDIS_SEARCH_KEY;
            }
            caches = new MultiTenantCache<String>(new RedisCache<>(host, port, password, expTimeSeconds, String.class, String.class));
        }
        local = caches instanceof AutoCloseable;

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
