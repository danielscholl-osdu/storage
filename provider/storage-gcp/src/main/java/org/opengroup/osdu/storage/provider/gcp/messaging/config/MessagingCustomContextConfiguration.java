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

package org.opengroup.osdu.storage.provider.gcp.messaging.config;

import java.util.Arrays;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.multitenancy.TenantInfoFactory;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.gcp.web.config.WebAppMainContextConfiguration;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;

/**
 * This context configuration serves for message receiving configuration not bounded to request scope.
 * Also, configured context does not serve incoming requests.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties
@PropertySource("classpath:application.properties")
@RequiredArgsConstructor
@ComponentScan(value = {
    "org.opengroup.osdu"
},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            value = {
                StorageApplication.class,
                WebAppMainContextConfiguration.class,
                TenantInfoFactory.class
            }
        )
    }
)
public class MessagingCustomContextConfiguration {

    private final ApplicationContext applicationContext;

    @PostConstruct
    public void setUp() {
        log.debug("Messaging context initialized with id: {}.", applicationContext.getId());
        log.debug("Messaging context status: {}.", applicationContext);
        String[] allBeansNames = applicationContext.getBeanDefinitionNames();
        log.debug("Messaging context beans definitions: {}.", Arrays.toString(allBeansNames));
    }

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new ThreadBeanFactoryPostProcessor();
    }
}
