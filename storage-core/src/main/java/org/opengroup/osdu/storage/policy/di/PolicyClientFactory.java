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

package org.opengroup.osdu.storage.policy.di;

import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.opengroup.osdu.core.common.policy.IPolicyFactory;
import org.opengroup.osdu.core.common.policy.PolicyAPIConfig;
import org.opengroup.osdu.core.common.policy.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "service.policy.enabled", havingValue = "true", matchIfMissing = false)
public class PolicyClientFactory extends AbstractFactoryBean<IPolicyFactory> {

    @Autowired
    private PolicyServiceConfiguration serviceConfiguration;

    @Autowired
    private HttpResponseBodyMapper httpResponseBodyMapper;

    @Override
    public Class<?> getObjectType() {
        return IPolicyFactory.class;
    }

    @Override
    protected IPolicyFactory createInstance() throws Exception {
        PolicyAPIConfig apiConfig = PolicyAPIConfig.builder()
                .rootUrl(serviceConfiguration.getPolicyApiEndpoint())
                .build();
        return new PolicyFactory(apiConfig, httpResponseBodyMapper);
    }
}
