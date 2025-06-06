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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.exception.NotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionMapperTest {

	@InjectMocks
	private GlobalExceptionMapper sut;

	@Mock
	private JaxRsDpsLog logger;

	@Test
	public void should_useValuesInAppExceptionInResponse_when_appExceptionIsHandledByGlobalExceptionMapper() {

		AppException exception = new AppException(409, "any reason", "any message");

		ResponseEntity response = this.sut.handleAppException(exception);
		assertEquals(409, response.getStatusCodeValue());

		verify(this.logger).warning("any message", exception);
	}

	@Test
	public void should_returnBadRequest_when_NotSupportedExceptionIsCaptured() {
		ValidationException diException = new ValidationException("my bad");

		ResponseEntity response = this.sut.handleValidationException(diException);
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
	}

	@Test
	public void should_logErrorMessage_when_statusCodeLargerThan499() {
		Exception originalException = new Exception("any message");
		AppException appException = new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Server error.",
				"An unknown error has occurred.", originalException);

		this.sut.handleAppException(appException);

		verify(this.logger).error("any message", appException);
	}

	@Test
	public void should_logWarningMessage_when_statusCodeSmallerThan499() {

		NotFoundException originalException = new NotFoundException("any message");
		AppException appException = new AppException(HttpStatus.SC_NOT_FOUND, "Resource not found.",
				"any message", originalException);

		this.sut.handleNotFoundException(originalException);

		verify(this.logger).warning("any message", appException);

	}

	@Test
	public void should_returnUnprocessableEntity_with_correct_reason_when_JsonProcessingException_Is_Captured() {
		JsonProcessingException exception = Mockito.mock(JsonProcessingException.class);
		ResponseEntity response = this.sut.handleJsonProcessingException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Failed to process JSON.", ((AppError)response.getBody()).getReason());
	}

	@Test
	public void should_returnUnprocessableEntity_with_correct_reason_when_UnrecognizedPropertyException_Is_Captured() {
		UnrecognizedPropertyException exception = Mockito.mock(UnrecognizedPropertyException.class);
		ResponseEntity response = this.sut.handleUnrecognizedPropertyException(exception);

		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusCodeValue());
		assertNotNull(response.getBody());
		assertEquals(AppError.class, response.getBody().getClass());
		assertEquals("Unrecognized property.", ((AppError)response.getBody()).getReason());
	}

	@Test
	public void should_returnNullResponse_when_BrokenPipeIOExceptionIsCaptured() {
		IOException ioException = new IOException("Broken pipe");

		ResponseEntity response = this.sut.handleIOException(ioException);

		assertNull(response);
	}

	@Test
	public void should_returnServiceUnavailable_when_IOExceptionIsCaptured() {
		IOException ioException = new IOException("Not broken yet");

		ResponseEntity response = this.sut.handleIOException(ioException);

		assertEquals(HttpStatus.SC_SERVICE_UNAVAILABLE, response.getStatusCodeValue());
	}


	@Test
	public void should_returnSortedErrorMessages_when_MethodArgumentNotValidExceptionCaptured() {
		//given
		HttpHeaders headers = null;
		HttpStatusCode status = null;
		WebRequest request = null;
		BindingResult bindingResult = Mockito.spy(BindingResult.class);
		MethodArgumentNotValidException ex = Mockito.mock(MethodArgumentNotValidException.class);
		ArgumentCaptor<String> errorMessage = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<AppException> appException = ArgumentCaptor.forClass(AppException.class);
		List<ObjectError> objectErrors = new ArrayList<>();
		objectErrors.add(new ObjectError("", "B_defaultMessage"));
		objectErrors.add(new ObjectError("", "C_defaultMessage"));
		String aDefaultMessage = "A_defaultMessage"; // inserted into a list as not a first parameter
		objectErrors.add(new ObjectError("", aDefaultMessage));
		objectErrors.add(new ObjectError("", "D_defaultMessage"));
		// when
		when(ex.getBindingResult()).thenReturn(bindingResult);
		when(bindingResult.getAllErrors()).thenReturn(objectErrors);
		// then trigger logic and capture values
		this.sut.handleMethodArgumentNotValid(ex, headers, status, request);
		verify(logger).warning(errorMessage.capture(), appException.capture());
		assertEquals(aDefaultMessage, errorMessage.getValue());
	}
}
