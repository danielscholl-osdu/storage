/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.messaging.thread;

public class ThreadScopeContextHolder {

    private static final ThreadLocal<ThreadScopeAttributes> threadScopeAttributesHolder = new InheritableThreadLocal<ThreadScopeAttributes>() {
        @Override
        protected ThreadScopeAttributes initialValue() {
            return new ThreadScopeAttributes();
        }
    };

    private ThreadScopeContextHolder() {
    }

    public static ThreadScopeAttributes getThreadScopeAttributes() {
        return threadScopeAttributesHolder.get();
    }

    public static void setThreadScopeAttributes(ThreadScopeAttributes accessor) {
        threadScopeAttributesHolder.set(accessor);
    }

    public static ThreadScopeAttributes currentThreadScopeAttributes() throws IllegalStateException {
        ThreadScopeAttributes accessor = threadScopeAttributesHolder.get();
        if (accessor == null) {
            throw new IllegalStateException("No thread scoped attributes.");
        } else {
            return accessor;
        }
    }
}
