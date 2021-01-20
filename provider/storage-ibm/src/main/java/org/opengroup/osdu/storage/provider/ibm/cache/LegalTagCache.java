/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm.cache;

import javax.inject.Inject;

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.springframework.stereotype.Component;

@Component("LegalTagCache")
public class LegalTagCache implements ICache<String, String> {

	@Inject
    private TenantInfo tenant;

    private final MultiTenantCache<String> caches;

    public LegalTagCache() {
        this.caches = new MultiTenantCache<>(
                new VmCache(60*60, 1000));
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

