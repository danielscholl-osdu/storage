// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.provider.azure.util;

import com.azure.cosmos.implementation.RequestRateTooLargeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.util.GlobalExceptionMapper;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RunWith(MockitoJUnitRunner.class)
public class GlobalAzureExceptionMapperTest {

    @InjectMocks
    private GlobalAzureExceptionMapper sut;

    @Mock
    private GlobalExceptionMapper mapper;

    @Test
    public void should_returnServiceUnavailable_with_correct_reason_when_RequestRateTooLargeException_Is_Captured() {
        RequestRateTooLargeException exception = Mockito.mock(RequestRateTooLargeException.class);
        AppError expectedBody = new AppError(SERVICE_UNAVAILABLE.value(), "Too many requests on cosmosdb.", "Cosmosdb error.");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, SERVICE_UNAVAILABLE));

        ResponseEntity response = this.sut.handleCosmosdbException(exception);
        assertEquals(503, response.getStatusCodeValue());
        assertEquals("Too many requests on cosmosdb.", ((AppError)response.getBody()).getReason());

    }

}

