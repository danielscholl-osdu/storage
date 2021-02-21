package org.opengroup.osdu.storage.provider.azure.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.springframework.stereotype.Component;

@Component
public class PolicyCache extends VmCache<String, Boolean> {

    public PolicyCache() {
        super(60*60, 1000);
    }
}
