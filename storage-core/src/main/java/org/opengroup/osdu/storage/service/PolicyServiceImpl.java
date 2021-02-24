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

package org.opengroup.osdu.storage.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.policy.PolicyRequest;
import org.opengroup.osdu.core.common.model.policy.PolicyResponse;
import org.opengroup.osdu.core.common.policy.IPolicyFactory;
import org.opengroup.osdu.core.common.policy.IPolicyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "management.policy.enabled", havingValue = "true", matchIfMissing = false)
public class PolicyServiceImpl implements IPolicyService {

    @Autowired
    private IPolicyFactory policyFactory;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private JaxRsDpsLog logger;

    @Override
    public PolicyResponse evaluatePolicy(PolicyRequest policy) {

        try {
            this.logger.info("policy service called");
            IPolicyProvider serviceClient = this.policyFactory.create(this.headers);
            return serviceClient.evaluatePolicy(policy);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Policy service unavailable", "Error making request to Policy service");
        }
    }
}

