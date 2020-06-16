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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import javax.validation.ValidationException;

import org.opengroup.osdu.core.common.model.http.AppException;
import javassist.NotFoundException;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.http.ResponseEntity;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
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
}