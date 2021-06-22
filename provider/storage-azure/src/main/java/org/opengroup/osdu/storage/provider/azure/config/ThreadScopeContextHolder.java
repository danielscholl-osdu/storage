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

public final class ThreadScopeContextHolder {

    private static final ThreadLocal<ThreadScopeContext> CONTEXT_HOLDER = ThreadLocal
            .withInitial(ThreadScopeContext::new);

    private ThreadScopeContextHolder() {
        // utility object, not allowed to create instances
    }

    /**
     * Get the thread specific context.
     *
     * @return thread scoped context
     */
    public static ThreadScopeContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * Set the thread specific context.
     *
     * @param context thread scoped context
     */
    public static void setContext(ThreadScopeContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }
}
