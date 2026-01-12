// Copyright 2017-2021, Schlumberger
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

package org.opengroup.osdu.storage.validation.impl;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.storage.validation.api.PatchOperationValidator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class MetadataPatchValidator implements PatchOperationValidator {

    private static final String TAGS_REGEX = ".+\\:.+";
    private static final String PATCH_OPERATION_REMOVE = "remove";

    private final ILegalService legalService;
    private final IEntitlementsAndCacheService entitlementsAndCacheService;
    private final DpsHeaders headers;

    public MetadataPatchValidator(ILegalService legalService,
                                  IEntitlementsAndCacheService entitlementsAndCacheService,
                                  DpsHeaders headers) {
        this.legalService = legalService;
        this.entitlementsAndCacheService = entitlementsAndCacheService;
        this.headers = headers;
    }

    @Override
    public void validateDuplicates(List<PatchOperation> ops) {
        Set<String> paths = new HashSet<>();
        for (PatchOperation op : ops) {
            String path = op.getPath();
            if (paths.contains(path)) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Duplicate paths", "Users can only update a path once per request.");
            }
            paths.add(path);
        }
    }

    @Override
    public void validateAcls(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            Set<String> valueSet = new HashSet<>(Arrays.asList(op.getValue()));
            String path = op.getPath();
            if (path.startsWith("/acl")) {
                if (!this.entitlementsAndCacheService.isValidAcl(this.headers, valueSet)) {
                    throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
                }
            }
        }
    }

    @Override
    public void validateLegalTags(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            String path = op.getPath();
            Set<String> valueSet = new HashSet<>(Arrays.asList(op.getValue()));
            if (path.startsWith("/legal")) {
                this.legalService.validateLegalTags(valueSet);
            }
        }
    }

    @Override
    public void validateTags(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            String path = op.getPath();
            if (path.startsWith("/tags")) {
                if (!PATCH_OPERATION_REMOVE.equals(op.getOp()) && !areTagsValid(op.getValue())) {
                    throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid tags", "Invalid tags values provided");
                }
            }
        }
    }

    private static boolean areTagsValid(String[] values) {
        return Stream.of(values).allMatch(value -> value.matches(TAGS_REGEX));
    }
}
