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

package org.opengroup.osdu.storage.provider.gcp.web.config;

import java.util.Arrays;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.MessagingCustomContextConfiguration;
import org.opengroup.osdu.storage.provider.gcp.messaging.jobs.OqmSubscriberManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;

/**
 * This context configuration excludes pulling message receiving and thread scope configuration.
 */
@Slf4j
@Configuration
@EnableAutoConfiguration
@PropertySource("classpath:application.properties")
@ComponentScan(
    value = {"org.opengroup.osdu"},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            value = {
                StorageApplication.class
            }
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = {"org\\.opengroup\\.osdu\\.storage\\.provider\\.gcp\\.messaging\\..*"}
        )
    }
)
public class WebAppMainContextConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void setUp() {
        log.debug("Main web app context initialized with id: {}.", applicationContext.getId());
        log.debug("Main web app context status: {}.", applicationContext);
        String[] allBeansNames = applicationContext.getBeanDefinitionNames();
        log.debug("Main web app context beans definitions: {}.", Arrays.toString(allBeansNames));
    }
}
