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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatch;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.ADD;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REPLACE;

public class JsonPatchValidator implements ConstraintValidator<ValidJsonPatch, JsonPatch> {
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 100;
    private static final String KIND = "/kind";
    private static final String TAGS = "/tags";
    private static final String REGEX_TAGS_PATH_FOR_ADD_OR_REMOVE_SINGLE_KEY_VALUE = TAGS.concat("/.+");
    private static final String ACL_VIEWERS = "/acl/viewers";
    private static final String ACL_OWNERS = "/acl/owners";
    private static final String LEGAL_TAGS = "/legal/legaltags";
    private static final String ANCESTRY_PARENTS = "/ancestry/parents";
    private static final String META = "/meta";
    private static final String DATA = "/data";
    private static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH = "(" + String.join("|", ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS) + ")";
    private static final String REGEX_ACLS_LEGAL_ANCESTRY_PATH_FOR_ADD_OR_REMOVE_SINGLE_VALUE = REGEX_ACLS_LEGAL_ANCESTRY_PATH + "/(\\d+|-)";
    private static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList(KIND, TAGS, ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS, DATA, META));
    private static final Set<String> ACLS_LEGAL_ANCESTY_PATHS = new HashSet<>(Arrays.asList(ACL_VIEWERS, ACL_OWNERS, LEGAL_TAGS, ANCESTRY_PARENTS));
    private static final Set<String> INVALID_PATHS_FOR_REMOVE_OPERATION = ACLS_LEGAL_ANCESTY_PATHS;
    private static final String OP = "op";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {

        validateOperationSize(jsonPatch);
        validateOperationType(jsonPatch);
        validatePathStartAndEnd(jsonPatch);

        validateKind(jsonPatch);
        validateTags(jsonPatch);
        validateAclLegalAncestry(jsonPatch);
        validateMeta(jsonPatch);
        validateRemoveOperation(jsonPatch);

        return true;
    }

    private void validateOperationSize(JsonPatch jsonPatch) {
        long operationsNumber = objectMapper.convertValue(jsonPatch, JsonNode.class).size();
        boolean isNumberOfOperationsValid = operationsNumber >= MIN_NUMBER && operationsNumber <= MAX_NUMBER;
        if (!isNumberOfOperationsValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_OPERATION_SIZE)
                    .build();
        }
    }

    private void validateOperationType(JsonPatch jsonPatch) {
        boolean isOperationTypeValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(OP)))
                .map(PatchOperations::forOperation)
                .allMatch(getAllowedOperations());
        if (!isOperationTypeValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_OPERATION)
                    .build();
        }
    }

    private void validatePathStartAndEnd(JsonPatch jsonPatch) {
        boolean isPathStartValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .allMatch(getAllowedPathStart());
        if (!isPathStartValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_START)
                    .build();
        }

        boolean isPathEndValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathEnd());
        if (!isPathEndValid) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_END)
                    .build();
        }
    }

    private void validateKind(JsonPatch jsonPatch) {
        StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(KIND))
                .forEach(operation -> {
                    if (addOperation().test(operation) || removeOperation().test(operation)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND)
                                .build();
                    }

                    boolean isValidPath = removeExtraQuotes(operation.get(PATH)).equals(KIND);
                    if (!isValidPath) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_PATH_FOR_KIND)
                                .build();
                    }

                    JsonNode valueNode = operation.get(VALUE);
                    if (valueNode.getClass() != TextNode.class) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_KIND)
                                .build();
                    }
                });
    }

    private void validateTags(JsonPatch jsonPatch) {
        StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(TAGS))
                .filter(addOrReplaceOperation())
                .forEach(operation -> {
                    String path = removeExtraQuotes(operation.get(PATH));
                    JsonNode valueNode = operation.get(VALUE);
                    if (TAGS.equals(path) && (valueNode.getClass() != ObjectNode.class)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_TAGS)
                                .build();

                    }
                    if (path.matches(REGEX_TAGS_PATH_FOR_ADD_OR_REMOVE_SINGLE_KEY_VALUE) && (valueNode.getClass() != TextNode.class)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_TAGS)
                                .build();
                    }
                });
    }

    private void validateAclLegalAncestry(JsonPatch jsonPatch) {
        StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(getAclsAndLegalTagsOperations())
                .filter(addOrReplaceOperation())
                .forEach(operation -> {
                    String path = removeExtraQuotes(operation.get(PATH));
                    JsonNode valueNode = operation.get(VALUE);
                    if (path.matches(REGEX_ACLS_LEGAL_ANCESTRY_PATH) && (valueNode.getClass() != ArrayNode.class)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_ACL_LEGAL_ANCESTRY)
                                .build();

                    }
                    if (path.matches(REGEX_ACLS_LEGAL_ANCESTRY_PATH_FOR_ADD_OR_REMOVE_SINGLE_VALUE) && (valueNode.getClass() != TextNode.class)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_SINGLE_VALUE_FORMAT_FOR_ACL_LEGAL_ANCESTRY)
                                .build();
                    }
                });
    }

    private void validateMeta(JsonPatch jsonPatch) {
        StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(META))
                .filter(addOrReplaceOperation())
                .forEach(operation -> {
                    String path = removeExtraQuotes(operation.get(PATH));
                    JsonNode valueNode = operation.get(VALUE);
                    if (META.equals(path) && (valueNode.getClass() != ArrayNode.class)) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUE_FORMAT_FOR_META)
                                .build();

                    }
                });
    }

    private void validateRemoveOperation(JsonPatch jsonPatch) {
        boolean isValidPathForRemoveOperations = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(removeOperation())
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathsForRemoveOperation());
        if (!isValidPathForRemoveOperations) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_FOR_REMOVE_OPERATION)
                    .build();
        }
    }

    private Predicate<String> getAllowedPathStart() {
        return path -> VALID_PATH_BEGINNINGS.stream().anyMatch(path::startsWith);
    }

    private Predicate<? super String> getInvalidPathEnd() {
        return path -> path.endsWith("/");
    }

    private Predicate<JsonNode> addOperation() {
        return operation -> ADD.equals(getPatchOperation(operation));
    }

    private Predicate<JsonNode> addOrReplaceOperation() {
        return operation -> {
            PatchOperations patchOperation = getPatchOperation(operation);
            return ADD.equals(patchOperation) || REPLACE.equals(patchOperation);
        };
    }

    private Predicate<JsonNode> removeOperation() {
        return operation -> REMOVE.equals(getPatchOperation(operation));
    }

    private Predicate<JsonNode> getAclsAndLegalTagsOperations() {
        return operation -> {
            String path = removeExtraQuotes(operation.get(PATH));
            return ACLS_LEGAL_ANCESTY_PATHS.stream().anyMatch(path::startsWith);
        };
    }

    private Predicate<String> getInvalidPathsForRemoveOperation() {
        return path -> INVALID_PATHS_FOR_REMOVE_OPERATION.stream().anyMatch(path::equals);
    }

    private Predicate<PatchOperations> getAllowedOperations() {
        return operation -> ADD.equals(operation) || REMOVE.equals(operation) || REPLACE.equals(operation);
    }

    private PatchOperations getPatchOperation(JsonNode operation) {
        return PatchOperations.forOperation(removeExtraQuotes(operation.get(OP)));
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

    private Predicate<JsonNode> pathStartsWith(String path) {
        return operation -> removeExtraQuotes(operation.get(PATH)).startsWith(path);
    }

}
