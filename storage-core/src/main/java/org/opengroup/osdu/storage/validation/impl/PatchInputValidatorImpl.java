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

import static org.opengroup.osdu.storage.util.api.PatchOperations.ADD;
import static org.opengroup.osdu.storage.util.api.PatchOperations.REMOVE;

@Component
public class PatchInputValidatorImpl implements PatchInputValidator {

    private static final String VALUE = "value";
    private static final String PATH = "path";
    private static final String OP = "op";

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
        Set<String> valueSet = getValueSet(jsonPatch, "/acl");
        if (!valueSet.isEmpty() && !entitlementsAndCacheService.isValidAcl(headers, valueSet)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
        }
    }

    @Override
    public void validateLegalTags(JsonPatch jsonPatch) {
        Set<String> valueSet = getValueSet(jsonPatch, "/legal");
        if (!valueSet.isEmpty()) {
            legalService.validateLegalTags(valueSet);
        }
    }

    @Override
    public void validateKind(JsonPatch jsonPatch) {
        Set<String> valueSet = new HashSet<>();
        StreamSupport.stream(mapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith("/kind"))
                .forEach(operation -> {
                    String operationType = removeExtraQuotes(operation.get(OP));
                    if (ADD.equals(PatchOperations.forOperation(operationType)) ||
                            REMOVE.equals(PatchOperations.forOperation(operationType))) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND)
                                .build();
                    }

                    JsonNode valueNode = operation.get(VALUE);
                    if (valueNode.getClass() == ArrayNode.class) {
                        throw RequestValidationException.builder()
                                .message(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_KIND)
                                .build();
                    } else if (valueNode.getClass() == TextNode.class) {
                        valueSet.add(removeExtraQuotes(valueNode));
                    }

                });
        for (String kind : valueSet) {
            if (!kind.matches(org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.KIND_REGEX)) {
                throw RequestValidationException.builder()
                        .message(String.format(ValidationDoc.KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION, kind))
                        .build();
            }
        }

    }

    @Override
    public void validateAncestry(JsonPatch jsonPatch) {
        //TODO: impl
//        "ancestry": {
//            "parents": [
//                  "opendes:well:rawHavingWksCreated1:1624008140672245"
//            ]
//        }
        //ancestry looks something like this. Some basic validation we can do is:
        // if any of the ops contains 'ancestry' in the path, then
        // if 'add' => at least one parent is present (all parents must have valid record ID. Check out RecordAncestryValidator from os-core-common library)
        // if 'replace' => at least one parent is present (same constraint as above)
        // if 'remove' => acc to RFC spec, remove shouldn't have 'value'. We must respect this constraint
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
