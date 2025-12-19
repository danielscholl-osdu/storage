/*
 *  Copyright 2025, Microsoft Corporation *
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

package org.opengroup.osdu.storage.util;


import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.feature.CommonFeatureFlagStateResolverUtil;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.info.FeatureFlagStateResolver;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.opengroup.osdu.storage.config.FeatureConstants;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = FeatureConstants.EXPOSE_FEATUREFLAG_ENABLED_PROPERTY, havingValue = "true", matchIfMissing = true)
public class FeatureFlagStateConfiguration {

  private final ITenantInfoService tenantInfoService;
  private final IFeatureFlag featureFlagService;

  @Bean
  @RequestScope
  public FeatureFlagStateResolver collaborationFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        COLLABORATIONS_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }
  
  @Bean
  @RequestScope
  public FeatureFlagStateResolver opaFFResolver() {
    return CommonFeatureFlagStateResolverUtil.buildCommonFFStateResolver(
        OPA_FEATURE_NAME,
        tenantInfoService,
        featureFlagService
    );
  }

}
