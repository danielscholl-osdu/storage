// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.azure.config;


import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.Scope;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thread scope which allows putting data in thread scope and clearing up afterwards.
 */
public class ThreadScope implements Scope, DisposableBean {
    @Autowired
    HttpServletRequest request;
    /**
     * Get bean for the given name in the "ThreadScope"
     * This is called for creating beans of DpsHeaders and ThreadDpsHeaders type in "ThreadScope"
     *
     * The two types are distinguished on the basis of Request Attributes in Request Context Holder.
     *
     * If Request Attributes is not null, it is api request thread.
     *
     * For a new Api request thread MDC context is cleared and then this function is called
     * before extracting and setting headers.
     * For api request, with MDC context map of size zero, we clear the thread context, to ensure
     * ThreadLocal variable values are not reused, by new threads.
     *
     * Next for new, api request, the function returns new bean of DPSHeader type, after adding it to the context.
     *
     * If the Request Attributes is null, it is a subscriber thread, here if the bean does not already
     * exists in object factory, we create a new bean of type ThreadDpsHeader and return , after adding it to the context.
     *
     * For both cases, if it is not a new api request or if it is not a new subscriber thread request,
     * we return the existing object from the object factory.
     *
     */
    public Object get(String name, ObjectFactory<?> factory) {
        ThreadScopeContext context = ThreadScopeContextHolder.getContext();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (null != requestAttributes && contextMap.size() == 0) {
            context.clear();
        }
        Object result = context.getBean(name);
        if (null == result && null != requestAttributes) {
            DpsHeaders headers = new DpsHeaders();
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

            Map<String, String> header = Collections
                    .list(request.getHeaderNames())
                    .stream()
                    .collect(Collectors.toMap(h -> h, request::getHeader));
            for (Map.Entry<String, String> entry : header.entrySet()) {
                headers.put(entry.getKey(), entry.getValue());
            }
            context.setBean(name, headers);
            MDC.setContextMap(header);

            return headers;
        } else if (null == result) {
            result = factory.getObject();
            context.setBean(name, result);
            return result;
        } else {
            return result;
        }
    }


    /**
     * Removes bean from scope.
     */
    public Object remove(String name) {
        ThreadScopeContext context = ThreadScopeContextHolder.getContext();
        return context.remove(name);
    }

    public void registerDestructionCallback(String name, Runnable callback) {
        ThreadScopeContextHolder.getContext().registerDestructionCallback(name, callback);
    }

    /**
     * Resolve the contextual object for the given key, if any. E.g. the HttpServletRequest object for key "request".
     */
    public Object resolveContextualObject(String key) {
        return null;
    }

    /**
     * Return the conversation ID for the current underlying scope, if any.
     * <p/>
     * In this case, it returns the thread name.
     */
    public String getConversationId() {
        return Thread.currentThread().getName();
    }

    @Override
    public void destroy() {
        ThreadScopeContextHolder.clearContext();
    }
}



