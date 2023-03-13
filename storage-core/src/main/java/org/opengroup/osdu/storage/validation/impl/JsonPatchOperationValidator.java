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
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.util.api.PatchOperations;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatchOperation;
import org.springframework.http.HttpStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static org.opengroup.osdu.storage.util.api.PatchOperations.ADD;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REPLACE;

public class JsonPatchOperationValidator implements ConstraintValidator<ValidJsonPatchOperation, JsonPatch> {
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 100;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isValid(JsonPatch jsonPatch, ConstraintValidatorContext context) {
        boolean isNumberOfOperationsValid;
        boolean isOperationTypeValid;

        long operationsNumber = objectMapper.convertValue(jsonPatch, JsonNode.class).size();
        isNumberOfOperationsValid = operationsNumber >= MIN_NUMBER && operationsNumber <= MAX_NUMBER;
        if (!isNumberOfOperationsValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_OPERATION_SIZE)
                    .build();
        }

        isOperationTypeValid = StreamSupport.stream(objectMapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .map(operation -> operation.get("op").toString().replace("\"", ""))
                .map(PatchOperations::forOperation)
                .allMatch(getAllowedOperations());
        if (!isOperationTypeValid) {
            throw RequestValidationException.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message(ValidationDoc.INVALID_PATCH_OPERATION)
                    .build();
        }
        return true;
    }

    private Predicate<PatchOperations> getAllowedOperations() {
        return operation -> ADD.equals(operation) || REMOVE.equals(operation) || REPLACE.equals(operation);
    }
}
