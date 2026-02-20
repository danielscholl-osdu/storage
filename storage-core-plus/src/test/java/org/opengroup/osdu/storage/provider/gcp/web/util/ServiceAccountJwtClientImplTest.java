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
package org.opengroup.osdu.storage.provider.gcp.web.util;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.auth.TokenProvider;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ServiceAccountJwtClientImplTest {

    @Mock
    private TokenProvider tokenProvider;

    private ServiceAccountJwtClientImpl serviceAccountJwtClient;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    private static final String TEST_TENANT = "test-tenant";
    private static final String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test.token";

    @BeforeEach
    void setUp() {
        serviceAccountJwtClient = new ServiceAccountJwtClientImpl(tokenProvider);

        // Attach ListAppender to capture log events in memory
        logger = (Logger) LoggerFactory.getLogger(ServiceAccountJwtClientImpl.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    // ==================== Class Structure ====================

    @Test
    void shouldHaveCorrectAnnotationsAndImplementInterface() {
        // Assert - all required annotations are present
        assertTrue(ServiceAccountJwtClientImpl.class.isAnnotationPresent(Primary.class));
        assertTrue(ServiceAccountJwtClientImpl.class.isAnnotationPresent(Component.class));
        assertTrue(ServiceAccountJwtClientImpl.class.isAnnotationPresent(RequestScope.class));

        // Assert - implements the correct interface
        assertTrue(IServiceAccountJwtClient.class.isAssignableFrom(ServiceAccountJwtClientImpl.class));
    }

    // ==================== Token Generation ====================

    @Test
    void getIdToken_shouldReturnBearerTokenAndDelegateToTokenProvider() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);

        // Act
        String result = serviceAccountJwtClient.getIdToken(TEST_TENANT);

        // Assert - correct Bearer format
        assertNotNull(result);
        assertTrue(result.startsWith("Bearer "));
        assertEquals("Bearer " + TEST_TOKEN, result);

        // Assert - token has exactly two parts: "Bearer" and the token
        String[] parts = result.split(" ", 2);
        assertEquals(2, parts.length);
        assertEquals("Bearer", parts[0]);
        assertEquals(TEST_TOKEN, parts[1]);

        // Assert - TokenProvider called exactly once
        verify(tokenProvider).getIdToken();
    }

    @Test
    void getIdToken_shouldHandleEdgeCaseTokens() {
        // Test 1: Empty token
        when(tokenProvider.getIdToken()).thenReturn("");
        assertEquals("Bearer ", serviceAccountJwtClient.getIdToken(TEST_TENANT));

        reset(tokenProvider);
        logAppender.list.clear();

        // Test 2: Null token
        when(tokenProvider.getIdToken()).thenReturn(null);
        assertEquals("Bearer null", serviceAccountJwtClient.getIdToken(TEST_TENANT));

        reset(tokenProvider);
        logAppender.list.clear();

        // Test 3: Long token (realistic JWT length ~578 chars)
        String longToken = "a".repeat(36) + "." + "a".repeat(200) + "." + "a".repeat(342);
        when(tokenProvider.getIdToken()).thenReturn(longToken);
        String longResult = serviceAccountJwtClient.getIdToken(TEST_TENANT);
        assertEquals("Bearer " + longToken, longResult);
        assertTrue(longResult.length() > 400);

        reset(tokenProvider);
        logAppender.list.clear();

        // Test 4: Token with spaces (should preserve)
        String tokenWithSpaces = "token with spaces";
        when(tokenProvider.getIdToken()).thenReturn(tokenWithSpaces);
        assertEquals("Bearer " + tokenWithSpaces, serviceAccountJwtClient.getIdToken(TEST_TENANT));
    }

    @Test
    void getIdToken_shouldReturnDifferentTokensOnRepeatedCalls() {
        // Arrange
        when(tokenProvider.getIdToken())
                .thenReturn("token1")
                .thenReturn("token2")
                .thenReturn("token3");

        // Act
        String result1 = serviceAccountJwtClient.getIdToken("tenant1");
        String result2 = serviceAccountJwtClient.getIdToken("tenant2");
        String result3 = serviceAccountJwtClient.getIdToken("tenant3");

        // Assert - each call gets its own token from TokenProvider
        assertEquals("Bearer token1", result1);
        assertEquals("Bearer token2", result2);
        assertEquals("Bearer token3", result3);
        verify(tokenProvider, times(3)).getIdToken();
    }

    @Test
    void getIdToken_shouldPropagateTokenProviderExceptions() {
        // Arrange
        when(tokenProvider.getIdToken()).thenThrow(new RuntimeException("Token generation failed"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> serviceAccountJwtClient.getIdToken(TEST_TENANT));
    }

    // ==================== Tenant Name Handling ====================

    @Test
    void getIdToken_shouldHandleVariousTenantNamesAndLogThem() {
        // Test 1: Valid tenant name
        when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);
        serviceAccountJwtClient.getIdToken("valid-tenant-123");
        assertLogContains("valid-tenant-123");

        reset(tokenProvider);
        logAppender.list.clear();

        // Test 2: Null tenant
        when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);
        String nullResult = serviceAccountJwtClient.getIdToken(null);
        assertEquals("Bearer " + TEST_TOKEN, nullResult);
        assertLogContains("null");

        logAppender.list.clear();

        // Test 3: Empty tenant
        String emptyResult = serviceAccountJwtClient.getIdToken("");
        assertEquals("Bearer " + TEST_TOKEN, emptyResult);
        assertTrue(getDebugLogMessages().stream()
                .anyMatch(msg -> msg.contains("Tenant name received for auth token is: ")));

        logAppender.list.clear();

        // Test 4: Special characters
        String specialResult = serviceAccountJwtClient.getIdToken("tenant@#$%^&*()");
        assertEquals("Bearer " + TEST_TOKEN, specialResult);
        assertLogContains("tenant@#$%^&*()");

        logAppender.list.clear();

        // Test 5: Long tenant name
        String longTenant = "very-long-tenant-name-with-multiple-segments-separated-by-hyphens";
        String longResult = serviceAccountJwtClient.getIdToken(longTenant);
        assertEquals("Bearer " + TEST_TOKEN, longResult);
        assertLogContains(longTenant);
    }

    // ==================== Logging Behavior ====================

    @Test
    void getIdToken_shouldLogExactlyOnceWithCorrectFormat() {
        // Arrange
        when(tokenProvider.getIdToken()).thenReturn(TEST_TOKEN);

        // Act
        serviceAccountJwtClient.getIdToken(TEST_TENANT);

        // Assert - exactly one DEBUG log statement
        long debugLogCount = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .count();
        assertEquals(1, debugLogCount);

        // Assert - log message matches expected format and contains tenant name
        List<String> debugMessages = getDebugLogMessages();
        assertTrue(debugMessages.stream()
                .anyMatch(msg -> msg.matches("Tenant name received for auth token is: .*")));
        assertTrue(debugMessages.stream()
                .anyMatch(msg -> msg.contains(TEST_TENANT)));
    }

    // ==================== Helper Methods ====================

    private List<String> getDebugLogMessages() {
        return logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void assertLogContains(String expected) {
        boolean found = getDebugLogMessages().stream()
                .anyMatch(msg -> msg.contains(expected));
        assertTrue(found, "Expected debug log containing: " + expected);
    }
}
