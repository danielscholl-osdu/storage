/*
 *  Copyright @ Microsoft Corporation
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

import lombok.Getter;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.opengroup.osdu.storage.provider.gcp.messaging.jobs.stub.OqmPubSubStub;
import org.opengroup.osdu.storage.provider.gcp.messaging.scope.override.ScopeModifierPostProcessor;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.util.ReflectionTestUtils;

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
                MessagingCustomContextConfiguration.class
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
  public LegalTagConsistencyValidator legalTagConsistencyValidator(ILegalService legalService,
      JaxRsDpsLog jaxRsDpsLog) {
    LegalTagConsistencyValidator legalTagConsistencyValidator = new LegalTagConsistencyValidator();
    ReflectionTestUtils.setField(legalTagConsistencyValidator, "legalService", legalService);
    ReflectionTestUtils.setField(legalTagConsistencyValidator, "logger", jaxRsDpsLog);
    return legalTagConsistencyValidator;
  }

  @Bean
  public BeanFactoryPostProcessor beanFactoryPostProcessor() {
    return new ScopeModifierPostProcessor();
  }
}
