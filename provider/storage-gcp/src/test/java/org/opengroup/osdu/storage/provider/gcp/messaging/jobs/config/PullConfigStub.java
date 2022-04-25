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

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs.config;

import lombok.Getter;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingCustomContextConfiguration;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.ThreadBeanFactoryPostProcessor;
import org.opengroup.osdu.storage.provider.gcp.messaging.jobs.stub.OqmPubSubStub;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ThreadStorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

@Getter
@Configuration
@EnableConfigurationProperties
@ComponentScan(value = {
    "org.opengroup.osdu.storage.provider.gcp.messaging"
},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            value = {
                MessagingCustomContextConfiguration.class,
                ThreadStorageAuditLogger.class
            }
        )
    }
)
public class PullConfigStub {


    @Bean
    public IMessageBus messageBusStub() {
        return new OqmPubSubStub();
    }

    @Bean
    public BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new ThreadBeanFactoryPostProcessor();
    }
}
