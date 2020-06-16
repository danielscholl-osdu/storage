package org.opengroup.osdu.storage.provider.gcp.middleware;

import com.google.cloud.storage.StorageException;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.util.GlobalExceptionMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ControllerAdvice
public class GcpExceptionMapper {

    public static final String ACCESS_DENIED_REASON = "Access denied";
    public static final String ACCESS_DENIED_MESSAGE = "The user is not authorized to perform this action";

    private GlobalExceptionMapper mapper;

    public GcpExceptionMapper(GlobalExceptionMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(StorageException.class)
    protected ResponseEntity<Object> handleStorageException(StorageException e) {
        return mapper.getErrorResponse(
                new AppException(HttpStatus.FORBIDDEN.value(), ACCESS_DENIED_REASON, ACCESS_DENIED_MESSAGE, e));
    }

}
