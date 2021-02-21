package org.opengroup.osdu.storage.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.storage.model.policy.PolicyStatus;
import org.springframework.stereotype.Component;

@Component
public class PolicyCache extends VmCache<String, PolicyStatus> {

    public PolicyCache() {
        super(30*60, 1000);
    }

    public boolean containsKey(final String key) {
        return this.get(key) != null;
    }
}
