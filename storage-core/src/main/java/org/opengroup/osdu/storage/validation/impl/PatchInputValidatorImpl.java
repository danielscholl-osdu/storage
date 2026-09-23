// Copyright 2017-2023, Schlumberger
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonpatch.JsonPatch;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;

@Component
public class PatchInputValidatorImpl implements PatchInputValidator {

    private static final String VALUE = "value";
    private static final String PATH = "path";
    private static final String OP = "op";
    private static final String KIND = "/kind";

    private final ObjectMapper mapper = new ObjectMapper();
    private final ILegalService legalService;
    private final IEntitlementsAndCacheService entitlementsAndCacheService;
    private final DpsHeaders headers;

    public PatchInputValidatorImpl(ILegalService legalService,
                                   IEntitlementsAndCacheService entitlementsAndCacheService,
                                   DpsHeaders headers) {
        this.legalService = legalService;
        this.entitlementsAndCacheService = entitlementsAndCacheService;
        this.headers = headers;
    }

    @Override
    public void validateDuplicates(JsonPatch jsonPatch) {
        Set<JsonNode> nonDuplicates = new HashSet<>();
        Set<JsonNode> duplicates = StreamSupport.stream(mapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(node -> !nonDuplicates.add(node))
                .collect(Collectors.toSet());

        if (!duplicates.isEmpty()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Duplicate items", "Each patch operation should be unique.");
        }
    }

    @Override
    public void validateAcls(JsonPatch jsonPatch) {
        Set<String> acls = getValueSet(jsonPatch, "/acl");
        if (!acls.isEmpty() && !entitlementsAndCacheService.isValidAcl(headers, acls)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
        }
    }

    @Override
    public void validateLegalTags(JsonPatch jsonPatch) {
        Set<String> legalTags = getValueSet(jsonPatch, "/legal");
        if (!legalTags.isEmpty()) {
            legalService.validateLegalTags(legalTags);
        }
    }

    @Override
    public void validateKind(JsonPatch jsonPatch) {
        Set<String> kinds = new HashSet<>();
        StreamSupport.stream(mapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(KIND))
                .forEach(operation -> kinds.add(removeExtraQuotes(operation.get(VALUE))));

        for (String kind : kinds) {
            if (!kind.matches(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.KIND_REGEX)) {
                throw RequestValidationException.builder()
                        .message(String.format(ValidationDoc.KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION, kind))
                        .build();
            }
        }
    }

    @Override
    public void validateAncestry(JsonPatch jsonPatch) {
        Set<String> parents = getValueSet(jsonPatch, "/ancestry/parents");

        for (String parent : parents) {
            if (!parent.matches(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.RECORD_ID_REGEX)) {
                throw RequestValidationException.builder()
                        .message(String.format(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PARENT_RECORD_ID_FORMAT, parent))
                        .build();
            }

            if (!parent.matches(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.RECORD_ID_WITH_VERSION_REGEX)) {
                throw RequestValidationException.builder()
                        .message(String.format(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PARENT_RECORD_VERSION_FORMAT, parent))
                        .build();
            }
        }
    }

    private Set<String> getValueSet(JsonPatch jsonPatch, String path) {
        Set<String> valueSet = new HashSet<>();
        StreamSupport.stream(mapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(path))
                .filter(notRemoveOperation())
                .forEach(operation -> {
                    JsonNode valueNode = operation.get(VALUE);
                    if (valueNode.getClass() == ArrayNode.class) {
                        StreamSupport.stream(mapper.convertValue(valueNode, ArrayNode.class).spliterator(), false)
                                .map(this::removeExtraQuotes)
                                .forEach(valueSet::add);
                    } else if (valueNode.getClass() == TextNode.class) {
                        valueSet.add(removeExtraQuotes(valueNode));
                    }
                });

        return valueSet;
    }

    private Predicate<JsonNode> pathStartsWith(String path) {
        return operation -> removeExtraQuotes(operation.get(PATH)).startsWith(path);
    }

    private Predicate<JsonNode> notRemoveOperation() {
        return operation -> !REMOVE.equals(PatchOperations.forOperation(removeExtraQuotes(operation.get(OP))));
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

}
