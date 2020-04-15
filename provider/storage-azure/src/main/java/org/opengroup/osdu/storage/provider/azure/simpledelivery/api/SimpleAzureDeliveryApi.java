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

package org.opengroup.osdu.storage.provider.azure.simpledelivery.api;

import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.api.payloads.GetResourcesRequestPayload;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.api.payloads.GetResourcesResponsePayload;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.domain.SrnToPresignedUrlMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

import javax.inject.Inject;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("delivery")
@RequestScope
public class SimpleAzureDeliveryApi {

    @Inject
    private SrnToPresignedUrlMapper mapper;

    @PostMapping("/GetFileSignedURL")
    @PreAuthorize("@authorizationFilter.hasRole('" +
            StorageRole.VIEWER + "', '" +
            StorageRole.CREATOR + "', '" +
            StorageRole.ADMIN + "')")
    public ResponseEntity<GetResourcesResponsePayload> getResources(@Valid @RequestBody GetResourcesRequestPayload in) {
        List<String> srns = in.getSrns();
        GetResourcesResponsePayload out = mapper.processAllSrns(srns);
        return new ResponseEntity<>(out, HttpStatus.OK);
    }
}
