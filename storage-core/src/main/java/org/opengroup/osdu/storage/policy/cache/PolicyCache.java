// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.policy.cache;

import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.model.policy.PolicyStatus;
import org.springframework.stereotype.Component;

import javax.inject.Named;

@Component
public class PolicyCache extends VmCache<String, PolicyStatus> {

    public PolicyCache(final @Named("POLICY_CACHE_TIMEOUT") int timeout) {
        super(timeout * 60, 1000);
    }

    public boolean containsKey(final String key) {
        return this.get(key) != null;
    }
}
