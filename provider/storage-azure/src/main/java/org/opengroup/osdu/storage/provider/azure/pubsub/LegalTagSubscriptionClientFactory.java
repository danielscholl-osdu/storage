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

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LegalTagSubscriptionClientFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionClientFactory.class);
    @Autowired
    AzureBootstrapConfig azureBootstrapConfig;
    @Autowired
    private ISubscriptionClientFactory subscriptionClientFactory;

    public SubscriptionClient getSubscriptionClient(String dataPartition, String serviceBusTopic, String serviceBusTopicSubscription) throws ServiceBusException, InterruptedException {
            return subscriptionClientFactory.getClient(dataPartition, serviceBusTopic, serviceBusTopicSubscription);
    }
}
