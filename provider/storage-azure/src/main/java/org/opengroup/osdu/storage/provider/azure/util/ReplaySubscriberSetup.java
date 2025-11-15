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

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeBeanFactoryPostProcessor;
import org.opengroup.osdu.storage.provider.azure.pubsub.ReplaySubscriptionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
@Component
public class ReplaySubscriberSetup implements ApplicationListener<ContextRefreshedEvent> {

    @Value("${feature.replay.enabled}")
    private Boolean setup;

    @Autowired
    private ReplaySubscriptionManager replaySubscriptionManager;

    @Bean
    public static BeanFactoryPostProcessor beanFactoryPostProcessor1() {
        return new ThreadScopeBeanFactoryPostProcessor();
    }

    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent contextRefreshedEvent) {
        if (Boolean.TRUE.equals(setup)) {
            log.info("Replay feature is enabled.");
            log.info("Subscribing to replay events.");
            try {
              replaySubscriptionManager.subscribeToEvents();
            }
            catch (Exception e) {
                log.error("Error while subscribing to replay events: {}", e.getMessage(), e);
                throw e;
            }
            log.info("Successfully subscribed to replay events.");
        }
        else {
            log.info("Replay feature is disabled.");
        }
    }
}

