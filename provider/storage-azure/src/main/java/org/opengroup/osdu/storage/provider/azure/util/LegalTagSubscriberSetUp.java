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

package org.opengroup.osdu.storage.provider.azure.util;

import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeBeanFactoryPostProcessor;
import org.opengroup.osdu.storage.provider.azure.pubsub.LegalTagSubscriptionManagerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;


@Component
public class LegalTagSubscriberSetUp implements ApplicationListener<ContextRefreshedEvent> {
    @Autowired
    private LegalTagSubscriptionManagerImpl legalTagSubscriptionManager;
    @Value("${azure.feature.legaltag-compliance-update.enabled}")
    private Boolean legalTagComplianceUpdateEnabled;

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new ThreadScopeBeanFactoryPostProcessor();
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        if(legalTagComplianceUpdateEnabled)
          legalTagSubscriptionManager.subscribeLegalTagsChangeEvent();
    }
}

