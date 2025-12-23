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
import org.opengroup.osdu.storage.provider.azure.config.conditional.AsyncProcessingEnabled;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ThreadDpsHeaders needs for the pubsub functionality related to the replay and legaltag-compliance-update features.
 * Replay and legaltag-compliance-update functionality works outside the HTTP scope and require
 * ThreadDpsHeaders with a custom ThreadScope.
 *
 * When related feature flags are disabled, this bean has no consumers, which
 * causes a Spring context error due to an unused custom-scoped bean.
 *
 * That's why @AsyncProcessingEnabled used here
 */
@Component
@Primary
@Scope(value = "ThreadScope", proxyMode = ScopedProxyMode.TARGET_CLASS)
@AsyncProcessingEnabled
public class ThreadDpsHeaders extends DpsHeaders {

    public void setThreadContext(String dataPartitionId, String correlationId, String userEmail) {
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, dataPartitionId);
        headers.put(DpsHeaders.CORRELATION_ID, correlationId);
        headers.put(DpsHeaders.USER_EMAIL, userEmail);

        this.addFromMap(headers);
    }

}
