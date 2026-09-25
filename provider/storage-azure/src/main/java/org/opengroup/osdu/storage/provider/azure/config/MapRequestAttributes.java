// Copyright 2017-2026, Schlumberger
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

import org.springframework.web.context.request.RequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A lightweight, map-backed {@link RequestAttributes} implementation used to activate Spring's
 * "request" scope on non-web worker threads (for example the Azure Service Bus subscriber thread
 * that processes legal-tag compliance changes).
 */
public class MapRequestAttributes implements RequestAttributes {

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final Map<String, Runnable> destructionCallbacks = new LinkedHashMap<>();
    private final String sessionId = UUID.randomUUID().toString();
    private boolean completed = false;

    @Override
    public Object getAttribute(String name, int scope) {
        if (scope != SCOPE_REQUEST) {
            return null;
        }
        return this.attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value, int scope) {
        if (scope != SCOPE_REQUEST) {
            return;
        }
        this.attributes.put(name, value);
    }

    @Override
    public void removeAttribute(String name, int scope) {
        if (scope != SCOPE_REQUEST) {
            return;
        }
        this.attributes.remove(name);
        this.destructionCallbacks.remove(name);
    }

    @Override
    public String[] getAttributeNames(int scope) {
        if (scope != SCOPE_REQUEST) {
            return new String[0];
        }
        return this.attributes.keySet().toArray(new String[0]);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback, int scope) {
        if (scope == SCOPE_REQUEST) {
            this.destructionCallbacks.put(name, callback);
        }
    }

    @Override
    public Object resolveReference(String key) {
        return null;
    }

    @Override
    public String getSessionId() {
        return this.sessionId;
    }

    @Override
    public Object getSessionMutex() {
        return this;
    }

    /**
     * Runs any registered destruction callbacks (e.g. to dispose request-scoped beans) and clears
     * state. Idempotent; call once the unit of work on the thread is finished.
     */
    public void requestCompleted() {
        if (this.completed) {
            return;
        }
        this.completed = true;
        for (Runnable callback : this.destructionCallbacks.values()) {
            callback.run();
        }
        this.destructionCallbacks.clear();
        this.attributes.clear();
    }
}
