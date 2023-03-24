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
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatch;
import org.springframework.http.HttpStatus;

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
    private static final Set<String> VALID_PATH_BEGINNINGS = new HashSet<>(Arrays.asList("/tags", "/acl/viewers", "/acl/owners", "/legal/legaltags", "/ancestry/parents", "/kind", "/data", "/meta"));
    private static final Set<String> INVALID_PATHS_FOR_ADD_OPERATION = new HashSet<>(Arrays.asList("/tags", "/acl/viewers", "/acl/owners", "/legal/legaltags", "/ancestry/parents"));
    private static final Set<String> INVALID_PATHS_FOR_REMOVE_OPERATION = new HashSet<>(Arrays.asList("/acl/viewers", "/acl/owners", "/legal/legaltags"));
    private static final String OP = "op";
    private static final String PATH = "path";
    private static final String VALUE = "value";
    private static final String KIND = "/kind";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {

        validateOperationSize(jsonPatch);
        validateOperationType(jsonPatch);
        validatePathStartAndEnd(jsonPatch);

        validateKind(jsonPatch);
        validateAddOperation(jsonPatch);
        validateReplaceOperation(jsonPatch);
        validateRemoveOperation(jsonPatch);

        return true;
    }

    private void validateOperationSize(JsonPatch jsonPatch) {
        long operationsNumber = objectMapper.convertValue(jsonPatch, JsonNode.class).size();
        boolean isNumberOfOperationsValid = operationsNumber >= MIN_NUMBER && operationsNumber <= MAX_NUMBER;
        if (!isNumberOfOperationsValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
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
                    .status(HttpStatus.BAD_REQUEST)
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
                    PatchOperations patchOperation = PatchOperations.forOperation(removeExtraQuotes(operation.get(OP)));
                    if (ADD.equals(patchOperation) || REMOVE.equals(patchOperation)) {
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

    private void validateAddOperation(JsonPatch jsonPatch) {
        //multiple values are not allowed for add operation
        boolean isAddOperationValueValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(addOperation())
                .map(operation -> operation.get(VALUE).getClass())
                .noneMatch(aClass -> aClass.equals(ArrayNode.class));
        if (!isAddOperationValueValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_VALUE_FOR_ADD_OPERATION)
                    .build();
        }

        boolean isValidPathForAddOperations = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(addOperation())
                .map(operation -> removeExtraQuotes(operation.get(PATH)))
                .noneMatch(getInvalidPathsForAddOperation());
        if (!isValidPathForAddOperations) {
            throw RequestValidationException.builder()
                    .message(ValidationDoc.INVALID_PATCH_PATH_FOR_ADD_OPERATION)
                    .build();
        }
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

    private void validateReplaceOperation(JsonPatch jsonPatch) {
        //single value is allowed for replace operation with the path ends with index(number) or endOfArray sign '-'
        boolean isReplaceOperationValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(replaceOperation())
                .filter(pathEndsWithNumberOrEndOfArraySign())
                .map(operation -> operation.get(VALUE).getClass())
                .noneMatch(aClass -> aClass.equals(ArrayNode.class));
        if (!isReplaceOperationValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_VALUE_FOR_REPLACE_OPERATION)
                    .build();
        }

        //TODO multiple values are allowed for replace operation with the path exactly /acls/viewers, acls/owners, /legal/legaltags, /ancestry/parents, ...
    }

    private Predicate<String> getAllowedPathStart() {
        return path -> VALID_PATH_BEGINNINGS.stream().anyMatch(path::startsWith);
    }

    private Predicate<? super String> getInvalidPathEnd() {
        return path -> path.endsWith("/");
    }

    private Predicate<JsonNode> addOperation() {
        return operation -> ADD.equals(PatchOperations.forOperation(removeExtraQuotes(operation.get(OP))));
    }

    private Predicate<JsonNode> replaceOperation() {
        return operation -> REPLACE.equals(PatchOperations.forOperation(removeExtraQuotes(operation.get(OP))));
    }

    private Predicate<JsonNode> removeOperation() {
        return operation -> REMOVE.equals(PatchOperations.forOperation(removeExtraQuotes(operation.get(OP))));
    }

    private Predicate<String> getInvalidPathsForAddOperation() {
        return path -> INVALID_PATHS_FOR_ADD_OPERATION.stream().anyMatch(path::equals);
    }

    private Predicate<? super String> getInvalidPathsForRemoveOperation() {
        return path -> INVALID_PATHS_FOR_REMOVE_OPERATION.stream().anyMatch(path::equals);
    }

    private Predicate<? super JsonNode> pathEndsWithNumberOrEndOfArraySign() {
        return operation -> {
            String path = removeExtraQuotes(operation.get(PATH));
            return path.matches(".+?\\d") || path.endsWith("/-");
        };
    }

    private Predicate<PatchOperations> getAllowedOperations() {
        return operation -> ADD.equals(operation) || REMOVE.equals(operation) || REPLACE.equals(operation);
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

    private Predicate<JsonNode> pathStartsWith(String path) {
        return operation -> removeExtraQuotes(operation.get(PATH)).startsWith(path);
    }

}
