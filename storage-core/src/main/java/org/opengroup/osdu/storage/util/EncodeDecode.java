package org.opengroup.osdu.storage.util;


import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;

@Component
public class EncodeDecode {

    public String deserializeCursor(String cursor) {
        if(StringUtils.isEmpty(cursor)) {
            return cursor;
        }
        try {
            return new String(Base64.getDecoder().decode(cursor));
        } catch (IllegalArgumentException e) {
            throw this.getInvalidCursorException(e);
        }
    }

    public String serializeCursor(String continuationToken) {
        if(StringUtils.isEmpty(continuationToken)) {
            return continuationToken;
        }
        return Base64.getEncoder().encodeToString(continuationToken.getBytes());
    }

    private AppException getInvalidCursorException(Exception e) {
        return new AppException(HttpStatus.SC_BAD_REQUEST, "Cursor invalid",
                "The requested cursor does not exist or is invalid", e);
    }

}
