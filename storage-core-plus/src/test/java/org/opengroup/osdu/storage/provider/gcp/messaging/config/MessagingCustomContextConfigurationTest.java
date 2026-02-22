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
package org.opengroup.osdu.storage.provider.gcp.messaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class MessagingCustomContextConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    private MessagingCustomContextConfiguration configuration;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        configuration = new MessagingCustomContextConfiguration(applicationContext);

        // Attach ListAppender to capture log events in memory for this test only
        logger = (Logger) LoggerFactory.getLogger(MessagingCustomContextConfiguration.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        // Detach appender to prevent log events leaking into other tests
        logger.detachAppender(logAppender);
    }

    // ==================== Annotation Verification ====================

    @Test
    void testAnnotations_shouldHaveAllRequiredSpringAnnotations() {
        assertTrue(MessagingCustomContextConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.Configuration.class));
        assertTrue(MessagingCustomContextConfiguration.class.isAnnotationPresent(
                org.springframework.boot.context.properties.EnableConfigurationProperties.class));
        assertTrue(MessagingCustomContextConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.PropertySource.class));
        assertTrue(MessagingCustomContextConfiguration.class.isAnnotationPresent(
                org.springframework.context.annotation.ComponentScan.class));
    }

    // ==================== Lifecycle Validation ====================

    @Test
    void testSetUp_shouldDelegateToApplicationContextAndLogCorrectly() {
        // Arrange
        String expectedContextId = "messaging-context-123";
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(applicationContext.getId()).thenReturn(expectedContextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert - ApplicationContext methods were called
        verify(applicationContext).getId();
        verify(applicationContext).getBeanDefinitionNames();

        // Assert - context ID is logged
        assertLogContains("Messaging context initialized with id: " + expectedContextId);

        // Assert - context status is logged
        assertLogContains("Messaging context status:");

        // Assert - all bean definitions are logged
        assertLogContains("Messaging context beans definitions:");
        assertLogContains("bean1");
        assertLogContains("bean2");
        assertLogContains("bean3");

        // Assert - exactly 3 debug log statements are produced
        long debugLogCount = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .count();
        assertEquals(3, debugLogCount, "setUp() should produce exactly 3 debug log statements");
    }

    // ==================== Edge Cases ====================

    @Test
    void testSetUp_shouldHandleEdgeCasesGracefully() {
        // Arrange - null context ID
        when(applicationContext.getId()).thenReturn(null);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});

        // Act & Assert - null context ID does not throw
        configuration.setUp();
        verify(applicationContext).getId();

        // Assert - empty bean definitions are logged as empty array
        assertLogContains("[]");

        // Reset to test large bean array
        logAppender.list.clear();
        when(applicationContext.getId()).thenReturn("test-id");

        String[] largeBeanArray = new String[100];
        for (int i = 0; i < 100; i++) {
            largeBeanArray[i] = "bean" + i;
        }
        when(applicationContext.getBeanDefinitionNames()).thenReturn(largeBeanArray);

        // Act
        configuration.setUp();

        // Assert - first and last beans in the large array are both logged
        assertLogContains("bean0");
        assertLogContains("bean99");
    }

    // ==================== Helper Methods ====================

    /**
     * Asserts that at least one DEBUG-level log message contains the given substring.
     */
    private void assertLogContains(String expected) {
        boolean found = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> msg.contains(expected));
        assertTrue(found, "Expected debug log containing: " + expected);
    }
}
