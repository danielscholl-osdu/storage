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

package org.opengroup.osdu.storage.di;

import java.util.ArrayList;
import java.util.List;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.multitenancy.ITenantInfoService;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.inject.Inject;

@RequestScope
@Component
public class TenantInfoService implements ITenantInfoService {
    @Inject
    private ITenantFactory tenantFactory;

    @Inject
    private DpsHeaders headers;

    @Override
    public TenantInfo getTenantInfo() {
        return tenantFactory.getTenantInfo(headers.getPartitionId());
    }

    @Override
    public List<TenantInfo> getAllTenantInfos() {
        return new ArrayList<>(tenantFactory.listTenantInfo());
    }
}
