package org.opengroup.osdu.storage.provider.azure.exception;

import org.springframework.lang.Nullable;

public class CosmosStoreException extends Exception {
    public CosmosStoreException(String msg) {
        super(msg);
    }

    public CosmosStoreException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
