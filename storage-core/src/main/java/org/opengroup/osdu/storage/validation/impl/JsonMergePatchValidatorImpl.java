/*
 * Copyright 2025 bp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.validation.impl;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.JsonMergePatchValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class JsonMergePatchValidatorImpl implements JsonMergePatchValidator {

    @Autowired
    private DpsHeaders headers;
    @Autowired
    private IEntitlementsAndCacheService entitlementsAndCacheService;
    @Autowired
    private ILegalService legalService;


    @Override
    public void validateACLs(Set<String> acls) {
        if (!acls.isEmpty() && !entitlementsAndCacheService.isValidAcl(headers, acls)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
        }
    }

    @Override
    public void validateKind(String kind) {
        if (!kind.matches(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.KIND_REGEX)) {
            throw RequestValidationException.builder()
                    .message(String.format(ValidationDoc.KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION, kind))
                    .build();
        }
    }

    @Override
    public void validateLegalTags(Set<String> legalTags) {
        if (!legalTags.isEmpty()) {
            legalService.validateLegalTags(legalTags);
        }
    }
}
