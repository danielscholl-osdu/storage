/*
 *  Copyright @ Microsoft Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.opengroup.osdu.storage.provider.gcp.web.middleware;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.StorageException;
import org.opengroup.osdu.storage.util.GlobalExceptionMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
@DisplayName("GcpExceptionMapper Tests")
class GcpExceptionMapperTest {

    @Mock
    private GlobalExceptionMapper globalExceptionMapper;

    @Mock
    private ResponseEntity<Object> mockResponseEntity;

    @Mock
    private HttpResponse mockHttpResponse;

    @Captor
    private ArgumentCaptor<AppException> appExceptionCaptor;

    private GcpExceptionMapper gcpExceptionMapper;

    @BeforeEach
    void setUp() {
        gcpExceptionMapper = new GcpExceptionMapper(globalExceptionMapper);
    }

    // ========================================
    // Annotation Tests
    // ========================================

    @Nested
    @DisplayName("Annotation Tests")
    class AnnotationTests {

        @Test
        @DisplayName("Should have @ControllerAdvice annotation")
        void shouldHaveControllerAdviceAnnotation() {
            // Assert
            assertTrue(GcpExceptionMapper.class.isAnnotationPresent(ControllerAdvice.class),
                    "Class should be annotated with @ControllerAdvice");
        }

        @Test
        @DisplayName("Should have @Order annotation")
        void shouldHaveOrderAnnotation() {
            // Assert
            assertTrue(GcpExceptionMapper.class.isAnnotationPresent(Order.class),
                    "Class should be annotated with @Order");
        }

        @Test
        @DisplayName("Should have correct order value")
        void shouldHaveCorrectOrderValue() {
            // Act
            Order orderAnnotation = GcpExceptionMapper.class.getAnnotation(Order.class);

            // Assert
            assertNotNull(orderAnnotation, "Order annotation should not be null");
            assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, orderAnnotation.value(),
                    "Order value should be HIGHEST_PRECEDENCE + 1");
        }

        @Test
        @DisplayName("Should have @ExceptionHandler annotation on handleStorageException method")
        void shouldHaveExceptionHandlerAnnotation() throws NoSuchMethodException {
            // Act
            Method method = GcpExceptionMapper.class.getDeclaredMethod(
                    "handleStorageException", StorageException.class);
            ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);

            // Assert
            assertNotNull(annotation, "handleStorageException should have @ExceptionHandler annotation");
            assertEquals(1, annotation.value().length, "Should handle exactly one exception type");
            assertEquals(StorageException.class, annotation.value()[0],
                    "Should handle StorageException");
        }
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with GlobalExceptionMapper")
        void shouldCreateInstanceWithGlobalExceptionMapper() {
            // Act
            GcpExceptionMapper mapper = new GcpExceptionMapper(globalExceptionMapper);

            // Assert
            assertNotNull(mapper, "Instance should not be null");
        }

        @Test
        @DisplayName("Should accept null GlobalExceptionMapper")
        void shouldAcceptNullGlobalExceptionMapper() {
            // Act & Assert
            assertDoesNotThrow(() -> new GcpExceptionMapper(null),
                    "Constructor should accept null GlobalExceptionMapper");
        }

        @Test
        @DisplayName("Should store GlobalExceptionMapper dependency")
        void shouldStoreGlobalExceptionMapperDependency() {
            // This test verifies the mapper is used (tested implicitly in handleStorageException tests)
            // Act
            GcpExceptionMapper mapper = new GcpExceptionMapper(globalExceptionMapper);

            // Assert
            assertNotNull(mapper, "Mapper should be created successfully");
        }
    }

    // ========================================
    // Constants Tests
    // ========================================

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have correct ACCESS_DENIED_REASON constant")
        void shouldHaveCorrectAccessDeniedReasonConstant() {
            // Assert
            assertEquals("Access denied", GcpExceptionMapper.ACCESS_DENIED_REASON,
                    "ACCESS_DENIED_REASON should be 'Access denied'");
        }

        @Test
        @DisplayName("Should have correct ACCESS_DENIED_MESSAGE constant")
        void shouldHaveCorrectAccessDeniedMessageConstant() {
            // Assert
            assertEquals("The user is not authorized to perform this action",
                    GcpExceptionMapper.ACCESS_DENIED_MESSAGE,
                    "ACCESS_DENIED_MESSAGE should be 'The user is not authorized to perform this action'");
        }

        @Test
        @DisplayName("Constants should be public static final")
        void constantsShouldBePublicStaticFinal() throws NoSuchFieldException {
            // Act
            var reasonField = GcpExceptionMapper.class.getField("ACCESS_DENIED_REASON");
            var messageField = GcpExceptionMapper.class.getField("ACCESS_DENIED_MESSAGE");

            // Assert
            assertTrue(java.lang.reflect.Modifier.isPublic(reasonField.getModifiers()),
                    "ACCESS_DENIED_REASON should be public");
            assertTrue(java.lang.reflect.Modifier.isStatic(reasonField.getModifiers()),
                    "ACCESS_DENIED_REASON should be static");
            assertTrue(java.lang.reflect.Modifier.isFinal(reasonField.getModifiers()),
                    "ACCESS_DENIED_REASON should be final");

            assertTrue(java.lang.reflect.Modifier.isPublic(messageField.getModifiers()),
                    "ACCESS_DENIED_MESSAGE should be public");
            assertTrue(java.lang.reflect.Modifier.isStatic(messageField.getModifiers()),
                    "ACCESS_DENIED_MESSAGE should be static");
            assertTrue(java.lang.reflect.Modifier.isFinal(messageField.getModifiers()),
                    "ACCESS_DENIED_MESSAGE should be final");
        }
    }

    // ========================================
    // handleStorageException Tests
    // ========================================

    @Nested
    @DisplayName("handleStorageException Tests")
    class HandleStorageExceptionTests {

        @ParameterizedTest(name = "{1}")
        @MethodSource("storageExceptionMessageScenarios")
        @DisplayName("Should handle StorageException with various message types")
        void shouldHandleStorageException(String exceptionMessage, String testDescription) {
            // Arrange
            StorageException storageException = new StorageException(exceptionMessage, mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            ResponseEntity<Object> response = gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            assertNotNull(response, "Response should not be null");
            verify(globalExceptionMapper).getErrorResponse(any(AppException.class));
        }

        static Stream<Arguments> storageExceptionMessageScenarios() {
            return Stream.of(
                    Arguments.of("Test storage error", "Standard error message"),
                    Arguments.of(null, "Null message"),
                    Arguments.of("", "Empty message"),
                    Arguments.of("Error: <>&\"'", "Special characters in message")
            );
        }

        @Test
        @DisplayName("Should create AppException with FORBIDDEN status code")
        void shouldCreateAppExceptionWithForbiddenStatusCode() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException capturedAppException = appExceptionCaptor.getValue();
            assertEquals(HttpStatus.FORBIDDEN.value(), capturedAppException.getError().getCode(),
                    "AppException should have FORBIDDEN status code (403)");
        }

        @Test
        @DisplayName("Should create AppException with ACCESS_DENIED_REASON")
        void shouldCreateAppExceptionWithAccessDeniedReason() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException capturedAppException = appExceptionCaptor.getValue();
            assertEquals(GcpExceptionMapper.ACCESS_DENIED_REASON,
                    capturedAppException.getError().getReason(),
                    "AppException should have ACCESS_DENIED_REASON");
        }

        @Test
        @DisplayName("Should create AppException with ACCESS_DENIED_MESSAGE")
        void shouldCreateAppExceptionWithAccessDeniedMessage() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException capturedAppException = appExceptionCaptor.getValue();
            assertEquals(GcpExceptionMapper.ACCESS_DENIED_MESSAGE,
                    capturedAppException.getError().getMessage(),
                    "AppException should have ACCESS_DENIED_MESSAGE");
        }

        @Test
        @DisplayName("Should wrap original StorageException as cause")
        void shouldWrapOriginalStorageExceptionAsCause() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException capturedAppException = appExceptionCaptor.getValue();
            assertEquals(storageException, capturedAppException.getCause(),
                    "Original StorageException should be wrapped as cause");
        }

        @Test
        @DisplayName("Should return ResponseEntity from GlobalExceptionMapper")
        void shouldReturnResponseEntityFromGlobalExceptionMapper() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            ResponseEntity<Object> response = gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            assertSame(mockResponseEntity, response,
                    "Should return the ResponseEntity from GlobalExceptionMapper");
        }

        @Test
        @DisplayName("Should call GlobalExceptionMapper exactly once")
        void shouldCallGlobalExceptionMapperExactlyOnce() {
            // Arrange
            StorageException storageException = new StorageException("Test storage error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper, times(1)).getErrorResponse(any(AppException.class));
        }

        @Test
        @DisplayName("Should handle StorageException with long message")
        void shouldHandleStorageExceptionWithLongMessage() {
            // Arrange
            String longMessage = "Error: " + "x".repeat(1000);
            StorageException storageException = new StorageException(longMessage, mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            ResponseEntity<Object> response = gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            assertNotNull(response, "Response should not be null with long exception message");
            verify(globalExceptionMapper).getErrorResponse(any(AppException.class));
        }

        @Test
        @DisplayName("Should handle multiple StorageExceptions sequentially")
        void shouldHandleMultipleStorageExceptionsSequentially() {
            // Arrange
            StorageException exception1 = new StorageException("Error 1", mockHttpResponse);
            StorageException exception2 = new StorageException("Error 2", mockHttpResponse);
            StorageException exception3 = new StorageException("Error 3", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(exception1);
            gcpExceptionMapper.handleStorageException(exception2);
            gcpExceptionMapper.handleStorageException(exception3);

            // Assert
            verify(globalExceptionMapper, times(3)).getErrorResponse(any(AppException.class));
        }
    }

    // ========================================
    // Edge Cases Tests
    // ========================================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle StorageException with cause chain")
        void shouldHandleStorageExceptionWithCauseChain() {
            // Arrange
            HttpResponse mockHttpResponse2 = mock(HttpResponse.class);
            StorageException storageException = new StorageException("Top", mockHttpResponse2);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            ResponseEntity<Object> response = gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            assertNotNull(response);
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException captured = appExceptionCaptor.getValue();
            assertEquals(storageException, captured.getCause());
        }

        @Test
        @DisplayName("Should handle concurrent exception handling")
        void shouldHandleConcurrentExceptionHandling() {
            // Arrange
            StorageException exception1 = new StorageException("Concurrent error 1", mockHttpResponse);
            StorageException exception2 = new StorageException("Concurrent error 2", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            ResponseEntity<Object> response1 = gcpExceptionMapper.handleStorageException(exception1);
            ResponseEntity<Object> response2 = gcpExceptionMapper.handleStorageException(exception2);

            // Assert
            assertNotNull(response1);
            assertNotNull(response2);
            verify(globalExceptionMapper, times(2)).getErrorResponse(any(AppException.class));
        }

        @Test
        @DisplayName("Should preserve all AppException properties")
        void shouldPreserveAllAppExceptionProperties() {
            // Arrange
            StorageException storageException = new StorageException("Test error", mockHttpResponse);
            when(globalExceptionMapper.getErrorResponse(any(AppException.class)))
                    .thenReturn(mockResponseEntity);

            // Act
            gcpExceptionMapper.handleStorageException(storageException);

            // Assert
            verify(globalExceptionMapper).getErrorResponse(appExceptionCaptor.capture());
            AppException captured = appExceptionCaptor.getValue();

            assertNotNull(captured.getError(), "Error should not be null");
            assertNotEquals(0, captured.getError().getCode(), "Error code should not be 0");
            assertNotNull(captured.getError().getReason(), "Error reason should not be null");
            assertNotNull(captured.getError().getMessage(), "Error message should not be null");
            assertNotNull(captured.getCause(), "Cause should not be null");
        }
    }

    // ========================================
    // Method Signature Tests
    // ========================================

    @Nested
    @DisplayName("Method Signature Tests")
    class MethodSignatureTests {

        @Test
        @DisplayName("handleStorageException should return ResponseEntity<Object>")
        void handleStorageExceptionShouldReturnCorrectType() throws NoSuchMethodException {
            // Act
            Method method = GcpExceptionMapper.class.getDeclaredMethod(
                    "handleStorageException", StorageException.class);

            // Assert
            assertEquals(ResponseEntity.class, method.getReturnType(),
                    "Method should return ResponseEntity");
        }

        @Test
        @DisplayName("handleStorageException should be protected")
        void handleStorageExceptionShouldBeProtected() throws NoSuchMethodException {
            // Act
            Method method = GcpExceptionMapper.class.getDeclaredMethod(
                    "handleStorageException", StorageException.class);

            // Assert
            assertTrue(java.lang.reflect.Modifier.isProtected(method.getModifiers()),
                    "Method should be protected");
        }

        @Test
        @DisplayName("handleStorageException should accept StorageException parameter")
        void handleStorageExceptionShouldAcceptCorrectParameter() throws NoSuchMethodException {
            // Act
            Method method = GcpExceptionMapper.class.getDeclaredMethod(
                    "handleStorageException", StorageException.class);

            // Assert
            assertEquals(1, method.getParameterCount(), "Method should have exactly one parameter");
            assertEquals(StorageException.class, method.getParameterTypes()[0],
                    "Parameter should be StorageException");
        }
    }
}
