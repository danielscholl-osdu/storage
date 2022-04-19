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

package org.opengroup.osdu.storage.provider.gcp.messaging.scope.override;

import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.gcp.messaging.config.ThreadBeanFactoryPostProcessor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Original class bean configuration bounded to request scope, extend purpose is to unbound it. Due to OQM specific, we cannot rely on events transferred via
 * HTTP requests, only pull subscriptions works with OQM. But this bean is configured only for Messaging context, original bean keeps working as usual for the
 * web app context.
 */

@Primary
@Component
@Scope(value = ThreadBeanFactoryPostProcessor.SCOPE_THREAD, proxyMode = TARGET_CLASS)
@RequiredArgsConstructor
public class ThreadTenantInfoFactory implements FactoryBean<TenantInfo> {

    private final ITenantFactory tenantFactory;
    private final DpsHeaders headers;

    @Override
    public TenantInfo getObject() throws Exception {
        String id = this.headers.getPartitionIdWithFallbackToAccountId();
        TenantInfo tenantInfo = this.tenantFactory.getTenantInfo(id);
        if (tenantInfo == null) {
            throw AppException.createUnauthorized(String.format("could not retrieve tenant info for data partition id: %s", id));
        }
        return tenantInfo;
    }

    @Override
    public Class<?> getObjectType() {
        return TenantInfo.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
