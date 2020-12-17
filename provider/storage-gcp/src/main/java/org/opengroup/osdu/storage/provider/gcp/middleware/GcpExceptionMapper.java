/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

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
