// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class RequestScopeUtilTest {

    private RequestScopeUtil requestScopeUtil;

    @Before
    public void setUp() {
        requestScopeUtil = new RequestScopeUtil();
        // Ensure request context is clean before each test
        RequestContextHolder.resetRequestAttributes();
    }

    @After
    public void tearDown() {
        // Clean up after each test
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void executeInRequestScope_WithNoHeaders_ShouldCreateDefaultRequestContext() {
        // Arrange
        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        AtomicReference<RequestAttributes> capturedAttributes = new AtomicReference<>();

        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            taskExecuted.set(true);
            capturedAttributes.set(RequestContextHolder.getRequestAttributes());
        });

        // Assert
        assertTrue("Task should have been executed", taskExecuted.get());
        assertNull("Request context should be cleaned up after execution", 
                RequestContextHolder.getRequestAttributes());
        assertNotNull("Request attributes should have been available during execution", 
                capturedAttributes.get());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) capturedAttributes.get();
        assertEquals("Default partition ID should be set", 
                "default", attributes.getRequest().getHeader("data-partition-id"));
        assertEquals("Default authorization header should be set", 
                "Bearer simulated-token-for-background-task", attributes.getRequest().getHeader("Authorization"));
        assertEquals("Default correlation ID should be set", 
                "simulated-correlation-id", attributes.getRequest().getHeader("correlation-id"));
    }

    @Test
    public void executeInRequestScope_WithCustomHeaders_ShouldUseProvidedHeaders() {
        // Arrange
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("data-partition-id", "custom-partition");
        customHeaders.put("Authorization", "Bearer custom-token");
        customHeaders.put("correlation-id", "custom-correlation-id");
        customHeaders.put("custom-header", "custom-value");

        AtomicReference<RequestAttributes> capturedAttributes = new AtomicReference<>();

        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            capturedAttributes.set(RequestContextHolder.getRequestAttributes());
        }, customHeaders);

        // Assert
        assertNotNull("Request attributes should have been available during execution", 
                capturedAttributes.get());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) capturedAttributes.get();
        assertEquals("Custom partition ID should be used", 
                "custom-partition", attributes.getRequest().getHeader("data-partition-id"));
        assertEquals("Custom authorization header should be used", 
                "Bearer custom-token", attributes.getRequest().getHeader("Authorization"));
        assertEquals("Custom correlation ID should be used", 
                "custom-correlation-id", attributes.getRequest().getHeader("correlation-id"));
        assertEquals("Custom header should be included", 
                "custom-value", attributes.getRequest().getHeader("custom-header"));
    }

    @Test
    public void executeInRequestScope_WithPartialHeaders_ShouldAddDefaultsForMissingHeaders() {
        // Arrange
        Map<String, String> partialHeaders = new HashMap<>();
        partialHeaders.put("custom-header", "custom-value");
        // Note: Not including data-partition-id or Authorization

        AtomicReference<RequestAttributes> capturedAttributes = new AtomicReference<>();

        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            capturedAttributes.set(RequestContextHolder.getRequestAttributes());
        }, partialHeaders);

        // Assert
        assertNotNull("Request attributes should have been available during execution", 
                capturedAttributes.get());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) capturedAttributes.get();
        assertEquals("Default partition ID should be added", 
                "default", attributes.getRequest().getHeader("data-partition-id"));
        assertEquals("Default authorization header should be added", 
                "Bearer simulated-token-for-background-task", attributes.getRequest().getHeader("Authorization"));
        assertEquals("Custom header should be included", 
                "custom-value", attributes.getRequest().getHeader("custom-header"));
    }

    @Test
    public void executeInRequestScope_WhenTaskThrowsException_ShouldCleanupContext() {
        // Arrange
        RuntimeException expectedException = new RuntimeException("Test exception");

        // Act & Assert
        try {
            requestScopeUtil.executeInRequestScope(() -> {
                throw expectedException;
            });
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            assertSame("Should throw the original exception", expectedException, e);
            assertNull("Request context should be cleaned up even when exception occurs", 
                    RequestContextHolder.getRequestAttributes());
        }
    }

    @Test
    public void executeInRequestScope_WithEmptyHeadersMap_ShouldUseDefaultHeaders() {
        // Arrange
        Map<String, String> emptyHeaders = new HashMap<>();
        AtomicReference<RequestAttributes> capturedAttributes = new AtomicReference<>();

        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            capturedAttributes.set(RequestContextHolder.getRequestAttributes());
        }, emptyHeaders);

        // Assert
        assertNotNull("Request attributes should have been available during execution", 
                capturedAttributes.get());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) capturedAttributes.get();
        assertEquals("Default partition ID should be used with empty map", 
                "default", attributes.getRequest().getHeader("data-partition-id"));
        assertEquals("Default authorization header should be used with empty map", 
                "Bearer simulated-token-for-background-task", attributes.getRequest().getHeader("Authorization"));
        assertEquals("Default correlation ID should be used with empty map", 
                "simulated-correlation-id", attributes.getRequest().getHeader("correlation-id"));
    }

    @Test
    public void executeInRequestScope_WithNullHeadersMap_ShouldUseDefaultHeaders() {
        // Arrange
        Map<String, String> nullHeaders = null;
        AtomicReference<RequestAttributes> capturedAttributes = new AtomicReference<>();

        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            capturedAttributes.set(RequestContextHolder.getRequestAttributes());
        }, nullHeaders);

        // Assert
        assertNotNull("Request attributes should have been available during execution", 
                capturedAttributes.get());
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) capturedAttributes.get();
        assertEquals("Default partition ID should be used with null map", 
                "default", attributes.getRequest().getHeader("data-partition-id"));
        assertEquals("Default authorization header should be used with null map", 
                "Bearer simulated-token-for-background-task", attributes.getRequest().getHeader("Authorization"));
        assertEquals("Default correlation ID should be used with null map", 
                "simulated-correlation-id", attributes.getRequest().getHeader("correlation-id"));
    }
}
