/*
 * Copyright © Amazon Web Services
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.aws.util;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * Utility class to execute code within a simulated request context.
 * This allows request-scoped beans to be used in non-request contexts like scheduled tasks.
 */
@Component
public class RequestScopeUtil {
    /**
     * Executes the given task within a simulated request context with custom headers.
     * This allows request-scoped beans to be used in non-request contexts like scheduled tasks.
     *
     * @param task The task to execute within the request context
     * @param headers Map of headers to add to the request
     */
    public void executeInRequestScope(Runnable task, Map<String, String> headers) {
        MockHttpServletRequest request = new MockHttpServletRequest();

        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Headers cannot be null or empty");
        } else {
            headers.forEach(request::addHeader);
        }
        
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            task.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
