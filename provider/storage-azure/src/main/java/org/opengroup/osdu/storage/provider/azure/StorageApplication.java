//  Copyright © Microsoft Corporation
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package org.opengroup.osdu.storage.provider.azure;

import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeBeanFactoryPostProcessor;
import org.opengroup.osdu.storage.provider.interfaces.LegalTagSubscriptionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Note: these exclusions are the result of duplicate dependencies being introduced in the
 * {@link //org.opengroup.osdu.is} package, which is pulled in through the os-core-lib-azure
 * mvn project. These duplicate beans are not needed by this application and so they are explicity
 * ignored.
 */
@ComponentScan(
        basePackages = {"org.opengroup.osdu"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "org.opengroup.osdu.is.*"),
        }
)
@SpringBootApplication
public class StorageApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(org.opengroup.osdu.storage.StorageApplication.class);

    public static void main(String[] args) {

        ApplicationContext context = SpringApplication.run(StorageApplication.class, args);
        try {
            LegalTagSubscriptionManager legalTagSubscriptionManager = context.getBean(LegalTagSubscriptionManager.class);
            legalTagSubscriptionManager.subscribeLegalTagsChangeEvent();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor() {
        return new ThreadScopeBeanFactoryPostProcessor();
    }
}
