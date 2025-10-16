package org.opengroup.osdu.storage.provider.azure.cache;

import org.opengroup.osdu.azure.multitenancy.TenantInfoDoc;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.springframework.stereotype.Component;

@Component
public class TenantInfoDocCache extends VmCache<String, TenantInfoDoc> {

    public TenantInfoDocCache() {
        super(30, 100);
    }
}