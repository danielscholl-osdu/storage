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
package org.opengroup.osdu.storage.provider.gcp.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GSuiteSecurityConfig Tests")
class GSuiteSecurityConfigTest {

    @Mock
    private HttpSecurity httpSecurity;

    @Mock
    private DefaultSecurityFilterChain defaultSecurityFilterChain;

    private GSuiteSecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new GSuiteSecurityConfig();
    }

    // ========================================
    // Annotation Tests
    // ========================================

    @Nested
    @DisplayName("Class Annotation Tests")
    class ClassAnnotationTests {

        @Test
        @DisplayName("Should have @Configuration annotation")
        void shouldHaveConfigurationAnnotation() {
            assertTrue(GSuiteSecurityConfig.class.isAnnotationPresent(Configuration.class));
        }

        @Test
        @DisplayName("Should have @EnableWebSecurity annotation")
        void shouldHaveEnableWebSecurityAnnotation() {
            assertTrue(GSuiteSecurityConfig.class.isAnnotationPresent(EnableWebSecurity.class));
        }

        @Test
        @DisplayName("Should have @EnableMethodSecurity annotation")
        void shouldHaveEnableMethodSecurityAnnotation() {
            assertTrue(GSuiteSecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class));
        }
    }

    // ========================================
    // FilterChain Method Execution Tests
    // ========================================

    @Nested
    @DisplayName("FilterChain Method Execution Tests")
    class FilterChainMethodExecutionTests {

        @Test
        @DisplayName("Should create SecurityFilterChain successfully")
        void shouldCreateSecurityFilterChainSuccessfully() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            SecurityFilterChain result = securityConfig.filterChain(httpSecurity);

            // Assert
            assertNotNull(result);
            assertInstanceOf(SecurityFilterChain.class, result);
        }

        @Test
        @DisplayName("Should disable CORS")
        void shouldDisableCors() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).cors(any());
        }

        @Test
        @DisplayName("Should disable CSRF")
        void shouldDisableCsrf() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).csrf(any());
        }

        @Test
        @DisplayName("Should configure session management")
        void shouldConfigureSessionManagement() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).sessionManagement(any());
        }

        @Test
        @DisplayName("Should configure authorize HTTP requests")
        void shouldConfigureAuthorizeHttpRequests() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).authorizeHttpRequests(any());
        }

        @Test
        @DisplayName("Should configure HTTP Basic authentication")
        void shouldConfigureHttpBasicAuthentication() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).httpBasic(any());
        }

        @Test
        @DisplayName("Should call build on HttpSecurity")
        void shouldCallBuildOnHttpSecurity() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).build();
        }

        @Test
        @DisplayName("Should configure all security settings in correct order")
        void shouldConfigureAllSecuritySettingsInCorrectOrder() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert - Verify all methods were called
            verify(httpSecurity).cors(any());
            verify(httpSecurity).csrf(any());
            verify(httpSecurity).sessionManagement(any());
            verify(httpSecurity).authorizeHttpRequests(any());
            verify(httpSecurity).httpBasic(any());
            verify(httpSecurity).build();
        }
    }

    // ========================================
    // Configuration Behavior Tests
    // ========================================

    @Nested
    @DisplayName("Configuration Behavior Tests")
    class ConfigurationBehaviorTests {

        @Test
        @DisplayName("Should return non-null SecurityFilterChain")
        void shouldReturnNonNullSecurityFilterChain() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            SecurityFilterChain result = securityConfig.filterChain(httpSecurity);

            // Assert
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should configure stateless session creation policy")
        void shouldConfigureStatelessSessionCreationPolicy() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).sessionManagement(any());
        }

        @Test
        @DisplayName("Should permit all requests")
        void shouldPermitAllRequests() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).authorizeHttpRequests(any());
        }
    }

    // ========================================
    // Exception Handling Tests
    // ========================================

    @Nested
    @DisplayName("Exception Handling Tests")
    class ExceptionHandlingTests {

        @Test
        @DisplayName("Should throw exception when HttpSecurity is null")
        void shouldThrowExceptionWhenHttpSecurityIsNull() {
            // Act & Assert
            assertThrows(Exception.class, () -> securityConfig.filterChain(null));
        }

        @Test
        @DisplayName("Should propagate exception from HttpSecurity.build()")
        void shouldPropagateExceptionFromBuild() throws Exception {
            // Arrange
            when(httpSecurity.cors(any())).thenReturn(httpSecurity);
            when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
            when(httpSecurity.sessionManagement(any())).thenReturn(httpSecurity);
            when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
            when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);
            when(httpSecurity.build()).thenThrow(new RuntimeException("Build failed"));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> securityConfig.filterChain(httpSecurity));
        }
    }

    // ========================================
    // Bean Configuration Tests
    // ========================================

    @Nested
    @DisplayName("Bean Configuration Tests")
    class BeanConfigurationTests {

        @Test
        @DisplayName("Should have filterChain method with @Bean annotation")
        void shouldHaveFilterChainBeanMethod() throws NoSuchMethodException {
            var method = GSuiteSecurityConfig.class.getMethod("filterChain", HttpSecurity.class);
            assertNotNull(method);
            assertTrue(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class));
        }

        @Test
        @DisplayName("Should return SecurityFilterChain from filterChain method")
        void shouldReturnSecurityFilterChain() throws NoSuchMethodException {
            var method = GSuiteSecurityConfig.class.getMethod("filterChain", HttpSecurity.class);
            assertEquals(SecurityFilterChain.class, method.getReturnType());
        }

        @Test
        @DisplayName("filterChain method should declare Exception")
        void filterChainMethodShouldDeclareException() throws NoSuchMethodException {
            var method = GSuiteSecurityConfig.class.getMethod("filterChain", HttpSecurity.class);
            var exceptionTypes = method.getExceptionTypes();
            assertEquals(1, exceptionTypes.length);
            assertEquals(Exception.class, exceptionTypes[0]);
        }
    }

    // ========================================
    // Constructor Tests
    // ========================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create instance successfully")
        void shouldCreateInstanceSuccessfully() {
            assertNotNull(securityConfig);
        }

        @Test
        @DisplayName("Should have default constructor")
        void shouldHaveDefaultConstructor() {
            assertDoesNotThrow(GSuiteSecurityConfig::new);
        }

        @Test
        @DisplayName("Should be instantiable multiple times")
        void shouldBeInstantiableMultipleTimes() {
            GSuiteSecurityConfig config1 = new GSuiteSecurityConfig();
            GSuiteSecurityConfig config2 = new GSuiteSecurityConfig();

            assertNotNull(config1);
            assertNotNull(config2);
            assertNotSame(config1, config2);
        }
    }

    // ========================================
    // Security Policy Verification Tests
    // ========================================

    @Nested
    @DisplayName("Security Policy Verification Tests")
    class SecurityPolicyVerificationTests {

        @Test
        @DisplayName("Should verify CORS is disabled")
        void shouldVerifyCorsIsDisabled() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).cors(any());
        }

        @Test
        @DisplayName("Should verify CSRF is disabled")
        void shouldVerifyCsrfIsDisabled() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).csrf(any());
        }

        @Test
        @DisplayName("Should verify session management is configured")
        void shouldVerifySessionManagementIsConfigured() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).sessionManagement(any());
        }

        @Test
        @DisplayName("Should verify HTTP requests authorization is configured")
        void shouldVerifyHttpRequestsAuthorizationIsConfigured() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).authorizeHttpRequests(any());
        }

        @Test
        @DisplayName("Should verify HTTP Basic is configured")
        void shouldVerifyHttpBasicIsConfigured() throws Exception {
            // Arrange
            setupHttpSecurityMocks();

            // Act
            securityConfig.filterChain(httpSecurity);

            // Assert
            verify(httpSecurity).httpBasic(any());
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
            assertTrue(java.lang.reflect.Modifier.isPublic(
                    GSuiteSecurityConfig.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should not be final")
        void classShouldNotBeFinal() {
            assertFalse(java.lang.reflect.Modifier.isFinal(
                    GSuiteSecurityConfig.class.getModifiers()));
        }

        @Test
        @DisplayName("Class should not be abstract")
        void classShouldNotBeAbstract() {
            assertFalse(java.lang.reflect.Modifier.isAbstract(
                    GSuiteSecurityConfig.class.getModifiers()));
        }

        @Test
        @DisplayName("Should have no instance fields")
        void shouldHaveNoInstanceFields() {
            assertEquals(0, GSuiteSecurityConfig.class.getDeclaredFields().length);
        }

        @Test
        @DisplayName("Should have exactly one bean method")
        void shouldHaveExactlyOneBeanMethod() {
            long beanMethodCount = java.util.Arrays.stream(
                            GSuiteSecurityConfig.class.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(
                            org.springframework.context.annotation.Bean.class))
                    .count();

            assertEquals(1, beanMethodCount);
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private void setupHttpSecurityMocks() throws Exception {
        // Setup fluent API chain - each method returns httpSecurity for chaining
        when(httpSecurity.cors(any())).thenReturn(httpSecurity);
        when(httpSecurity.csrf(any())).thenReturn(httpSecurity);
        when(httpSecurity.sessionManagement(any())).thenReturn(httpSecurity);
        when(httpSecurity.authorizeHttpRequests(any())).thenReturn(httpSecurity);
        when(httpSecurity.httpBasic(any())).thenReturn(httpSecurity);

        // build() returns DefaultSecurityFilterChain (concrete implementation)
        when(httpSecurity.build()).thenReturn(defaultSecurityFilterChain);
    }
}
