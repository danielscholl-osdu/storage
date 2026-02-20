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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;

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
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.gcp.web.config.WebAppMainContextConfiguration;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class WebAppMainContextConfigurationTest {

    @Mock
    private ApplicationContext applicationContext;

    private WebAppMainContextConfiguration configuration;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        configuration = new WebAppMainContextConfiguration();
        ReflectionTestUtils.setField(configuration, "applicationContext", applicationContext);

        // Attach ListAppender to capture log events in memory
        logger = (Logger) LoggerFactory.getLogger(WebAppMainContextConfiguration.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    // ==================== Annotations ====================

    @Test
    void shouldHaveAllRequiredAnnotationsWithCorrectValues() throws NoSuchFieldException {
        // Assert - @Configuration present
        assertTrue(WebAppMainContextConfiguration.class.isAnnotationPresent(Configuration.class));

        // Assert - @EnableAutoConfiguration present
        assertTrue(WebAppMainContextConfiguration.class.isAnnotationPresent(EnableAutoConfiguration.class));

        // Assert - @PropertySource with correct value
        PropertySource propertySource = WebAppMainContextConfiguration.class.getAnnotation(PropertySource.class);
        assertNotNull(propertySource);
        assertArrayEquals(new String[]{"classpath:application.properties"}, propertySource.value());

        // Assert - @ComponentScan with correct base package
        ComponentScan componentScan = WebAppMainContextConfiguration.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan);
        assertArrayEquals(new String[]{"org.opengroup.osdu"}, componentScan.value());

        // Assert - ApplicationContext field is @Autowired
        java.lang.reflect.Field field = WebAppMainContextConfiguration.class
                .getDeclaredField("applicationContext");
        assertTrue(field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class));
    }

    @Test
    void componentScan_shouldHaveCorrectExcludeFilters() {
        // Arrange
        ComponentScan componentScan = WebAppMainContextConfiguration.class.getAnnotation(ComponentScan.class);
        assertNotNull(componentScan);
        ComponentScan.Filter[] filters = componentScan.excludeFilters();

        // Assert - at least 2 filters
        assertTrue(filters.length >= 2);

        // Assert - correct filter types
        long assignableTypeCount = Arrays.stream(filters)
                .filter(filter -> filter.type() == FilterType.ASSIGNABLE_TYPE)
                .count();
        long regexTypeCount = Arrays.stream(filters)
                .filter(filter -> filter.type() == FilterType.REGEX)
                .count();
        assertTrue(assignableTypeCount >= 1);
        assertTrue(regexTypeCount >= 1);

        // Assert - excludes StorageApplication
        boolean excludesStorageApp = Arrays.stream(filters)
                .filter(filter -> filter.type() == FilterType.ASSIGNABLE_TYPE)
                .flatMap(filter -> Arrays.stream(filter.value()))
                .anyMatch(clazz -> clazz.equals(StorageApplication.class));
        assertTrue(excludesStorageApp);

        // Assert - excludes messaging package via regex
        String[] patterns = Arrays.stream(filters)
                .filter(filter -> filter.type() == FilterType.REGEX)
                .flatMap(filter -> Arrays.stream(filter.pattern()))
                .toArray(String[]::new);
        assertTrue(patterns.length > 0);
        String expectedPattern = "org\\.opengroup\\.osdu\\.storage\\.provider\\.gcp\\.messaging\\..*";
        assertTrue(Arrays.asList(patterns).contains(expectedPattern));
    }

    // ==================== setUp() Lifecycle ====================

    @Test
    void setUp_shouldLogContextDetailsWithCorrectFormat() {
        // Arrange
        String expectedContextId = "webapp-context-456";
        String[] beanNames = {"bean1", "bean2", "bean3"};
        when(applicationContext.getId()).thenReturn(expectedContextId);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(beanNames);

        // Act
        configuration.setUp();

        // Assert - correct ApplicationContext method calls
        verify(applicationContext).getId();
        verify(applicationContext).getBeanDefinitionNames();

        // Assert - exactly 3 DEBUG log statements
        long debugLogCount = logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .count();
        assertEquals(3, debugLogCount);

        List<String> logMessages = getDebugLogMessages();

        // Assert - log messages have correct prefixes and content
        assertTrue(logMessages.stream()
                .anyMatch(msg -> msg.startsWith("Main web app context initialized") &&
                        msg.contains(expectedContextId)));
        assertTrue(logMessages.stream()
                .anyMatch(msg -> msg.startsWith("Main web app context status")));
        assertTrue(logMessages.stream()
                .anyMatch(msg -> msg.startsWith("Main web app context beans definitions") &&
                        msg.contains("bean1") &&
                        msg.contains("bean2") &&
                        msg.contains("bean3")));
    }

    @Test
    void setUp_shouldHandleEdgeCases() {
        // Test 1: Null context ID
        when(applicationContext.getId()).thenReturn(null);
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});
        configuration.setUp();
        verify(applicationContext).getId();

        reset(applicationContext);
        logAppender.list.clear();

        // Test 2: Empty bean definitions
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(new String[]{});
        configuration.setUp();
        assertTrue(getDebugLogMessages().stream()
                .anyMatch(msg -> msg.contains("[]")));

        reset(applicationContext);
        logAppender.list.clear();

        // Test 3: Large bean array (100 beans)
        String[] largeBeanArray = new String[100];
        for (int i = 0; i < 100; i++) {
            largeBeanArray[i] = "bean" + i;
        }
        when(applicationContext.getId()).thenReturn("test-id");
        when(applicationContext.getBeanDefinitionNames()).thenReturn(largeBeanArray);
        configuration.setUp();
        verify(applicationContext).getBeanDefinitionNames();
        assertTrue(getDebugLogMessages().stream()
                .anyMatch(msg -> msg.contains("bean0") && msg.contains("bean99")));
    }

    // ==================== Helper Methods ====================

    private List<String> getDebugLogMessages() {
        return logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
