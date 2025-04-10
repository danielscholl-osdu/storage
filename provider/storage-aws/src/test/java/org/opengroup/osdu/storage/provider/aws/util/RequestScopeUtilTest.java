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

    @Test(expected = IllegalArgumentException.class)
    public void executeInRequestScope_WithNullHeaders_ShouldThrowException() {
        // Arrange
        Map<String, String> nullHeaders = null;

        // Act - should throw IllegalArgumentException
        requestScopeUtil.executeInRequestScope(() -> {
            // This should not execute
        }, nullHeaders);
    }

    @Test(expected = IllegalArgumentException.class)
    public void executeInRequestScope_WithEmptyHeaders_ShouldThrowException() {
        // Arrange
        Map<String, String> emptyHeaders = new HashMap<>();

        // Act - should throw IllegalArgumentException
        requestScopeUtil.executeInRequestScope(() -> {
            // This should not execute
        }, emptyHeaders);
    }

    @Test
    public void executeInRequestScope_WithCustomHeaders_ShouldUseProvidedHeaders() {
        // Arrange
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("data-partition-id", "custom-partition");
        customHeaders.put("Authorization", "Bearer custom-token");
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
        assertEquals("Custom header should be included", 
                "custom-value", attributes.getRequest().getHeader("custom-header"));
    }

    @Test
    public void executeInRequestScope_WhenTaskThrowsException_ShouldCleanupContext() {
        // Arrange
        RuntimeException expectedException = new RuntimeException("Test exception");
        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", "test-partition");
        headers.put("Authorization", "Bearer test-token");

        // Act & Assert
        try {
            requestScopeUtil.executeInRequestScope(() -> {
                throw expectedException;
            }, headers);
            fail("Expected exception was not thrown");
        } catch (RuntimeException e) {
            assertSame("Should throw the original exception", expectedException, e);
            assertNull("Request context should be cleaned up even when exception occurs", 
                    RequestContextHolder.getRequestAttributes());
        }
    }

    @Test
    public void executeInRequestScope_ShouldLogHeadersExceptAuthorization() {
        // This test verifies that headers are logged but can't directly test the logging output
        // We're just ensuring the method runs without exceptions when logging headers
        
        // Arrange
        Map<String, String> headers = new HashMap<>();
        headers.put("data-partition-id", "test-partition");
        headers.put("Authorization", "Bearer test-token");
        headers.put("custom-header", "custom-value");
        
        AtomicBoolean executed = new AtomicBoolean(false);
        
        // Act
        requestScopeUtil.executeInRequestScope(() -> {
            executed.set(true);
        }, headers);
        
        // Assert
        assertTrue("Task should have been executed", executed.get());
        assertNull("Request context should be cleaned up after execution", 
                RequestContextHolder.getRequestAttributes());
    }
}
