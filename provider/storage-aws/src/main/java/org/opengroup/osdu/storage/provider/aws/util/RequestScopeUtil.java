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

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Utility class to execute code within a simulated request context.
 * This allows request-scoped beans to be used in non-request contexts like scheduled tasks.
 */
@Component
public class RequestScopeUtil {
    private static final Logger LOGGER = Logger.getLogger(RequestScopeUtil.class.getName());
    
    /**
     * Executes the given task within a simulated request context.
     * This allows request-scoped beans to be used in non-request contexts like scheduled tasks.
     *
     * @param task The task to execute within the request context
     */
    public void executeInRequestScope(Runnable task) {
        executeInRequestScope(task, null);
    }
    
    /**
     * Executes the given task within a simulated request context with custom headers.
     * This allows request-scoped beans to be used in non-request contexts like scheduled tasks.
     *
     * @param task The task to execute within the request context
     * @param headers Map of headers to add to the request
     */
    public void executeInRequestScope(Runnable task, Map<String, String> headers) {
        LOGGER.info("Creating simulated request context for background task");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        // Add default headers if no custom headers are provided
        if (headers == null || headers.isEmpty()) {
            request.addHeader("data-partition-id", "default");
            request.addHeader("Authorization", "Bearer simulated-token-for-background-task");
            request.addHeader("correlation-id", "simulated-correlation-id");
        } else {
            // Add custom headers from the message
            headers.forEach((key, value) -> {
                LOGGER.info("Adding header to simulated request: " + key + "=" + value);
                request.addHeader(key, value);
            });
            
            // Ensure critical headers exist
            if (!headers.containsKey("data-partition-id")) {
                request.addHeader("data-partition-id", "default");
            }
            
            if (!headers.containsKey("Authorization")) {
                request.addHeader("Authorization", "Bearer simulated-token-for-background-task");
            }
        }
        
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            task.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            LOGGER.info("Cleaned up simulated request context");
        }
    }
}
