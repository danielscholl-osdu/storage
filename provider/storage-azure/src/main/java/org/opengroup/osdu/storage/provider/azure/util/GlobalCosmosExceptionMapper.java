package org.opengroup.osdu.storage.provider.azure.util;

import com.azure.cosmos.implementation.RequestRateTooLargeException;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.util.GlobalExceptionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalCosmosExceptionMapper {

    private GlobalExceptionMapper mapper;

    public GlobalCosmosExceptionMapper(GlobalExceptionMapper mapper) {
        this.mapper = mapper;
    }

    @ExceptionHandler(RequestRateTooLargeException.class)
    protected ResponseEntity<Object> handleCosmosdbException(Exception e) {
        return mapper.getErrorResponse(
                new AppException(HttpStatus.SERVICE_UNAVAILABLE.value(), "Too many requests on cosmosdb.",
                        e.getMessage(), e));
    }

}


