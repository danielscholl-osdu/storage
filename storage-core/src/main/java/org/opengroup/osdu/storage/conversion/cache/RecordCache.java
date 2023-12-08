package org.opengroup.osdu.storage.conversion.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.springframework.stereotype.Component;

import javax.inject.Named;

@Component
public class RecordCache extends VmCache<String, Record> {

    public RecordCache(final @Named("RECORD_CACHE_TIMEOUT") int timeout) {
        super(timeout * 60, 1000);
    }

    public boolean containsKey(final String key) {
        return this.get(key) != null;
    }

    public String getCacheKey(String partitionId, String recordId) {
        return String.format("%s-record-%s", partitionId, recordId);
    }
}
