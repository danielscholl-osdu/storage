package org.opengroup.osdu.storage.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;


@RunWith(MockitoJUnitRunner.class)
public class GlobalOtherExceptionMapperTest {

    @InjectMocks
    private GlobalOtherExceptionMapper sut;

    @Mock
    private GlobalExceptionMapper mapper;

    @Test
    public void should_useGenericValuesInResponse_when_exceptionIsHandledByGlobalExceptionMapper() {
        Exception exception = new Exception("any message");
        AppError expectedBody = new AppError(INTERNAL_SERVER_ERROR.value(), "Server error.", "An unknown error has occurred.");

        when(mapper.getErrorResponse(any(AppException.class))).thenReturn(new ResponseEntity<>(expectedBody, INTERNAL_SERVER_ERROR));

        ResponseEntity response = this.sut.handleGeneralException(exception);
        assertEquals(500, response.getStatusCodeValue());
    }

}
