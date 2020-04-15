// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.util;

import javax.validation.ValidationException;

import javassist.NotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.opengroup.osdu.core.common.model.http.AppException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class GlobalExceptionMapper extends ResponseEntityExceptionHandler {

    @Autowired
    private JaxRsDpsLog logger;

    @ExceptionHandler(AppException.class)
    protected ResponseEntity<Object> handleAppException(AppException e) {
        return this.getErrorResponse(e);
    }

    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<Object> handleValidationException(ValidationException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.BAD_REQUEST.value(), "Validation error.", e.getMessage(), e));
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<Object> handleNotFoundException(NotFoundException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.NOT_FOUND.value(), "Resource not found.", e.getMessage(), e));
    }

    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.FORBIDDEN.value(), "Access denied", e.getMessage(), e));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(Exception e) {
        return this.getErrorResponse(
                new AppException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Server error.",
                        "An unknown error has occurred.", e));
    }

    private ResponseEntity<Object> getErrorResponse(AppException e) {

        String exceptionMsg = e.getOriginalException() != null
                ? e.getOriginalException().getMessage()
                : e.getError().getMessage();

        if (e.getError().getCode() > 499) {
            this.logger.error(exceptionMsg, e);
        } else {
            this.logger.warning(exceptionMsg, e);
        }

        return new ResponseEntity<Object>(e.getError(), HttpStatus.resolve(e.getError().getCode()));
    }
}