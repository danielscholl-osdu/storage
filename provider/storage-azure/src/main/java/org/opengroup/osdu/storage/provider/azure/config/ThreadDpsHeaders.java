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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
@Component
@Primary
@ConditionalOnProperty(value = "azure.feature.legaltag-compliance-update.enabled", havingValue = "true", matchIfMissing = false)
@Scope(value = "ThreadScope", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ThreadDpsHeaders extends DpsHeaders {

    public void setThreadContext(String dataPartitionId, String correlationId, String userEmail) {
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, dataPartitionId);
        headers.put(DpsHeaders.CORRELATION_ID, correlationId);
        headers.put(DpsHeaders.USER_EMAIL, userEmail);

        this.addFromMap(headers);
    }

}
