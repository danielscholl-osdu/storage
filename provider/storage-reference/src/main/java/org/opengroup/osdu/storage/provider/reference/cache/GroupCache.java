package org.opengroup.osdu.storage.provider.reference.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.springframework.stereotype.Component;

@Component
public class GroupCache extends VmCache<String, Groups> {

  public GroupCache() {
    super(5 * 60, 1000);
  }
}