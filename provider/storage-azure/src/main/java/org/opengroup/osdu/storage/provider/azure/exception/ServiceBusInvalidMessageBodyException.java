package org.opengroup.osdu.storage.provider.azure.exception;

import org.springframework.lang.Nullable;

public class ServiceBusInvalidMessageBodyException extends Exception {
    public ServiceBusInvalidMessageBodyException(String msg) {
        super(msg);
    }

    public ServiceBusInvalidMessageBodyException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
