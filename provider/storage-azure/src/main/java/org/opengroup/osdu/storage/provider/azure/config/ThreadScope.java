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

import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeContext;
import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeContextHolder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

/**
 * Thread scope which allows putting data in thread scope and clearing up afterwards.
 */

public class ThreadScope implements Scope, DisposableBean {

    /**
     * Get bean for given name in the "ThreadScope".
     */
    public Object get(String name, ObjectFactory<?> factory) {
        ThreadScopeContext context = ThreadScopeContextHolder.getContext();

        Object result = context.getBean(name);
        if (null == result) {
            result = factory.getObject();
            context.setBean(name, result);
        }
        return result;
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



