package org.opengroup.osdu.storage.provider.reference.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.springframework.stereotype.Component;

@Component
public class SchemaCache extends VmCache<String, Schema> {

  public SchemaCache() {
    super(5 * 60, 1000);
  }
}