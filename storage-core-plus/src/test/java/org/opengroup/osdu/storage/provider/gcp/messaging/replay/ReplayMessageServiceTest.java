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
package org.opengroup.osdu.storage.provider.gcp.messaging.replay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.IReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReplayMessageService Tests")
class ReplayMessageServiceTest {

    @Mock
    private IReplayService replayService;

    @InjectMocks
    private ReplayMessageService replayMessageService;

    @Mock
    private ReplayMessage replayMessage;

    // ========================================
    // Class Annotation Tests
    // ========================================

    @Nested
    @DisplayName("Class Annotation Tests")
    class ClassAnnotationTests {

        @Test
        @DisplayName("Should have @Component annotation")
        void shouldHaveComponentAnnotation() {
            assertTrue(ReplayMessageService.class.isAnnotationPresent(Component.class));
        }

        @Test
        @DisplayName("Should have @Scope annotation with SINGLETON")
        void shouldHaveScopeSingletonAnnotation() {
            assertTrue(ReplayMessageService.class.isAnnotationPresent(Scope.class));
            Scope scope = ReplayMessageService.class.getAnnotation(Scope.class);
            assertEquals("singleton", scope.value());
        }

        @Test
        @DisplayName("Should have @ConditionalOnProperty annotation")
        void shouldHaveConditionalOnPropertyAnnotation() {
            assertTrue(ReplayMessageService.class.isAnnotationPresent(ConditionalOnProperty.class));
        }

        @Test
        @DisplayName("Should have correct ConditionalOnProperty configuration")
        void shouldHaveCorrectConditionalOnPropertyConfiguration() {
            ConditionalOnProperty annotation = ReplayMessageService.class
                    .getAnnotation(ConditionalOnProperty.class);

            assertNotNull(annotation);
            // annotation.value() returns String[], need to get first element
            assertEquals(1, annotation.value().length);
            assertEquals("feature.replay.enabled", annotation.value()[0]);
            assertEquals("true", annotation.havingValue());
        }

        @Test
        @DisplayName("Should be a Spring component")
        void shouldBeSpringComponent() {
            assertTrue(ReplayMessageService.class.isAnnotationPresent(Component.class));
            assertTrue(ReplayMessageService.class.isAnnotationPresent(Scope.class));
        }
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance with IReplayService")
        void shouldCreateInstanceWithIReplayService() {
            // Arrange
            IReplayService mockService = mock(IReplayService.class);

            // Act
            ReplayMessageService service = new ReplayMessageService(mockService);

            // Assert
            assertNotNull(service);
        }

        @Test
        @DisplayName("Should store IReplayService dependency")
        void shouldStoreIReplayServiceDependency() throws Exception {
            // Arrange
            IReplayService mockService = mock(IReplayService.class);
            ReplayMessageService service = new ReplayMessageService(mockService);

            // Act
            Field field = ReplayMessageService.class.getDeclaredField("replayService");
            field.setAccessible(true);
            IReplayService storedService = (IReplayService) field.get(service);

            // Assert
            assertSame(mockService, storedService);
        }

        @Test
        @DisplayName("Should handle null IReplayService in constructor")
        void shouldHandleNullIReplayService() {
            // Act & Assert - Constructor accepts null but will fail when methods are called
            assertDoesNotThrow(() -> new ReplayMessageService(null));
        }
    }

    // ========================================
    // processReplayMessage() Tests
    // ========================================

    @Nested
    @DisplayName("processReplayMessage() Tests")
    class ProcessReplayMessageTests {

        @Test
        @DisplayName("Should delegate to replayService.processReplayMessage")
        void shouldDelegateToReplayService() {
            // Act
            replayMessageService.processReplayMessage(replayMessage);

            // Assert
            verify(replayService).processReplayMessage(replayMessage);
        }

        @Test
        @DisplayName("Should pass correct ReplayMessage to service")
        void shouldPassCorrectReplayMessageToService() {
            // Arrange
            ArgumentCaptor<ReplayMessage> captor = ArgumentCaptor.forClass(ReplayMessage.class);

            // Act
            replayMessageService.processReplayMessage(replayMessage);

            // Assert
            verify(replayService).processReplayMessage(captor.capture());
            assertSame(replayMessage, captor.getValue());
        }

        @Test
        @DisplayName("Should handle null ReplayMessage")
        void shouldHandleNullReplayMessage() {
            // Act
            replayMessageService.processReplayMessage(null);

            // Assert
            verify(replayService).processReplayMessage(null);
        }

        @Test
        @DisplayName("Should handle multiple processReplayMessage calls")
        void shouldHandleMultipleProcessReplayMessageCalls() {
            // Arrange
            ReplayMessage message1 = mock(ReplayMessage.class);
            ReplayMessage message2 = mock(ReplayMessage.class);
            ReplayMessage message3 = mock(ReplayMessage.class);

            // Act
            replayMessageService.processReplayMessage(message1);
            replayMessageService.processReplayMessage(message2);
            replayMessageService.processReplayMessage(message3);

            // Assert
            verify(replayService, times(3)).processReplayMessage(any(ReplayMessage.class));
            verify(replayService).processReplayMessage(message1);
            verify(replayService).processReplayMessage(message2);
            verify(replayService).processReplayMessage(message3);
        }

        @Test
        @DisplayName("Should propagate exception from replayService")
        void shouldPropagateExceptionFromReplayService() {
            // Arrange
            doThrow(new RuntimeException("Processing failed"))
                    .when(replayService).processReplayMessage(replayMessage);

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> replayMessageService.processReplayMessage(replayMessage));
        }

        @Test
        @DisplayName("Should call replayService exactly once per invocation")
        void shouldCallReplayServiceExactlyOnce() {
            // Act
            replayMessageService.processReplayMessage(replayMessage);

            // Assert
            verify(replayService, times(1)).processReplayMessage(replayMessage);
        }
    }

    // ========================================
    // processFailure() Tests
    // ========================================

    @Nested
    @DisplayName("processFailure() Tests")
    class ProcessFailureTests {

        @Test
        @DisplayName("Should delegate to replayService.processFailure")
        void shouldDelegateToReplayServiceProcessFailure() {
            // Act
            replayMessageService.processFailure(replayMessage);

            // Assert
            verify(replayService).processFailure(replayMessage);
        }

        @Test
        @DisplayName("Should pass correct ReplayMessage to service")
        void shouldPassCorrectReplayMessageToService() {
            // Arrange
            ArgumentCaptor<ReplayMessage> captor = ArgumentCaptor.forClass(ReplayMessage.class);

            // Act
            replayMessageService.processFailure(replayMessage);

            // Assert
            verify(replayService).processFailure(captor.capture());
            assertSame(replayMessage, captor.getValue());
        }

        @Test
        @DisplayName("Should handle null ReplayMessage")
        void shouldHandleNullReplayMessage() {
            // Act
            replayMessageService.processFailure(null);

            // Assert
            verify(replayService).processFailure(null);
        }

        @Test
        @DisplayName("Should handle multiple processFailure calls")
        void shouldHandleMultipleProcessFailureCalls() {
            // Arrange
            ReplayMessage message1 = mock(ReplayMessage.class);
            ReplayMessage message2 = mock(ReplayMessage.class);
            ReplayMessage message3 = mock(ReplayMessage.class);

            // Act
            replayMessageService.processFailure(message1);
            replayMessageService.processFailure(message2);
            replayMessageService.processFailure(message3);

            // Assert
            verify(replayService, times(3)).processFailure(any(ReplayMessage.class));
            verify(replayService).processFailure(message1);
            verify(replayService).processFailure(message2);
            verify(replayService).processFailure(message3);
        }

        @Test
        @DisplayName("Should propagate exception from replayService")
        void shouldPropagateExceptionFromReplayService() {
            // Arrange
            doThrow(new RuntimeException("Failure processing failed"))
                    .when(replayService).processFailure(replayMessage);

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> replayMessageService.processFailure(replayMessage));
        }

        @Test
        @DisplayName("Should call replayService exactly once per invocation")
        void shouldCallReplayServiceExactlyOnce() {
            // Act
            replayMessageService.processFailure(replayMessage);

            // Assert
            verify(replayService, times(1)).processFailure(replayMessage);
        }
    }

    // ========================================
    // Class Structure Tests
    // ========================================

    @Nested
    @DisplayName("Class Structure Tests")
    class ClassStructureTests {

        @Test
        @DisplayName("Class should be public")
        void classShouldBePublic() {
            assertTrue(Modifier.isPublic(ReplayMessageService.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should not be final")
        void classShouldNotBeFinal() {
            assertFalse(Modifier.isFinal(ReplayMessageService.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should not be abstract")
        void classShouldNotBeAbstract() {
            assertFalse(Modifier.isAbstract(ReplayMessageService.class.getModifiers()));
        }

        @Test
        @DisplayName("Should have exactly two fields (replayService + log from @Slf4j)")
        void shouldHaveExactlyTwoFields() {
            // @Slf4j generates a 'log' field, plus we have 'replayService'
            assertEquals(2, ReplayMessageService.class.getDeclaredFields().length);
        }

        @Test
        @DisplayName("replayService field should be private final")
        void replayServiceFieldShouldBePrivateFinal() throws NoSuchFieldException {
            Field field = ReplayMessageService.class.getDeclaredField("replayService");
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }

        @Test
        @DisplayName("Should have exactly two public methods")
        void shouldHaveExactlyTwoPublicMethods() {
            long publicMethodCount = java.util.Arrays.stream(ReplayMessageService.class.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()))
                    .count();
            assertEquals(2, publicMethodCount);
        }

        @Test
        @DisplayName("Should be in correct package")
        void shouldBeInCorrectPackage() {
            assertEquals("org.opengroup.osdu.storage.provider.gcp.messaging.replay",
                    ReplayMessageService.class.getPackageName());
        }
    }

    // ========================================
    // Method Signature Tests
    // ========================================

    @Nested
    @DisplayName("Method Signature Tests")
    class MethodSignatureTests {

        @Test
        @DisplayName("processReplayMessage should have correct signature")
        void processReplayMessageShouldHaveCorrectSignature() throws NoSuchMethodException {
            var method = ReplayMessageService.class.getMethod("processReplayMessage", ReplayMessage.class);

            assertNotNull(method);
            assertEquals(void.class, method.getReturnType());
            assertEquals(1, method.getParameterCount());
            assertEquals(ReplayMessage.class, method.getParameterTypes()[0]);
        }

        @Test
        @DisplayName("processFailure should have correct signature")
        void processFailureShouldHaveCorrectSignature() throws NoSuchMethodException {
            var method = ReplayMessageService.class.getMethod("processFailure", ReplayMessage.class);

            assertNotNull(method);
            assertEquals(void.class, method.getReturnType());
            assertEquals(1, method.getParameterCount());
            assertEquals(ReplayMessage.class, method.getParameterTypes()[0]);
        }

        @Test
        @DisplayName("Both methods should be public")
        void bothMethodsShouldBePublic() throws NoSuchMethodException {
            var processReplayMethod = ReplayMessageService.class.getMethod("processReplayMessage", ReplayMessage.class);
            var processFailureMethod = ReplayMessageService.class.getMethod("processFailure", ReplayMessage.class);

            assertTrue(Modifier.isPublic(processReplayMethod.getModifiers()));
            assertTrue(Modifier.isPublic(processFailureMethod.getModifiers()));
        }

        @Test
        @DisplayName("Methods should not declare any checked exceptions")
        void methodsShouldNotDeclareCheckedExceptions() throws NoSuchMethodException {
            var processReplayMethod = ReplayMessageService.class.getMethod("processReplayMessage", ReplayMessage.class);
            var processFailureMethod = ReplayMessageService.class.getMethod("processFailure", ReplayMessage.class);

            assertEquals(0, processReplayMethod.getExceptionTypes().length);
            assertEquals(0, processFailureMethod.getExceptionTypes().length);
        }
    }

    // ========================================
    // Behavior Verification Tests
    // ========================================

    @Nested
    @DisplayName("Behavior Verification Tests")
    class BehaviorVerificationTests {

        @Test
        @DisplayName("Should not modify ReplayMessage before passing to service")
        void shouldNotModifyReplayMessageBeforePassingToService() {
            // Act
            replayMessageService.processReplayMessage(replayMessage);

            // Assert
            verify(replayService).processReplayMessage(same(replayMessage));
        }

        @Test
        @DisplayName("Should be stateless wrapper around IReplayService")
        void shouldBeStatelessWrapperAroundIReplayService() {
            // Arrange
            ReplayMessage msg1 = mock(ReplayMessage.class);
            ReplayMessage msg2 = mock(ReplayMessage.class);

            // Act
            replayMessageService.processReplayMessage(msg1);
            replayMessageService.processReplayMessage(msg2);

            // Assert - Each call should be independent
            verify(replayService).processReplayMessage(msg1);
            verify(replayService).processReplayMessage(msg2);
        }

        @Test
        @DisplayName("Should handle rapid successive calls")
        void shouldHandleRapidSuccessiveCalls() {
            // Act
            for (int i = 0; i < 100; i++) {
                replayMessageService.processReplayMessage(replayMessage);
            }

            // Assert
            verify(replayService, times(100)).processReplayMessage(replayMessage);
        }

        @Test
        @DisplayName("Should not cache or modify behavior between calls")
        void shouldNotCacheOrModifyBehaviorBetweenCalls() {
            // Arrange
            ReplayMessage msg1 = mock(ReplayMessage.class);
            ReplayMessage msg2 = mock(ReplayMessage.class);

            // Act
            replayMessageService.processFailure(msg1);
            replayMessageService.processFailure(msg2);

            // Assert - Each call should be treated independently
            verify(replayService, times(1)).processFailure(msg1);
            verify(replayService, times(1)).processFailure(msg2);
        }
    }

    // ========================================
    // Edge Cases Tests
    // ========================================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle same ReplayMessage processed multiple times")
        void shouldHandleSameReplayMessageProcessedMultipleTimes() {
            // Act
            replayMessageService.processReplayMessage(replayMessage);
            replayMessageService.processReplayMessage(replayMessage);
            replayMessageService.processReplayMessage(replayMessage);

            // Assert
            verify(replayService, times(3)).processReplayMessage(replayMessage);
        }

        @Test
        @DisplayName("Should handle interleaved null and non-null messages")
        void shouldHandleInterleavedNullAndNonNullMessages() {
            // Arrange
            ReplayMessage validMessage = mock(ReplayMessage.class);

            // Act
            replayMessageService.processReplayMessage(null);
            replayMessageService.processReplayMessage(validMessage);
            replayMessageService.processReplayMessage(null);

            // Assert
            verify(replayService, times(2)).processReplayMessage(null);
            verify(replayService, times(1)).processReplayMessage(validMessage);
        }

        @Test
        @DisplayName("Should not interfere with concurrent operations")
        void shouldNotInterfereWithConcurrentOperations() {
            // Arrange
            ReplayMessage msg1 = mock(ReplayMessage.class);
            ReplayMessage msg2 = mock(ReplayMessage.class);

            // Act - Simulate concurrent operations
            replayMessageService.processReplayMessage(msg1);
            replayMessageService.processFailure(msg2);

            // Assert - Both operations should complete independently
            verify(replayService).processReplayMessage(msg1);
            verify(replayService).processFailure(msg2);
        }
    }
}
