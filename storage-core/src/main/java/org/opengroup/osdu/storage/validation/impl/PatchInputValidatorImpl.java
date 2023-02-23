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
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
public class PatchInputValidatorImpl implements PatchInputValidator {

    private static final String VALUE = "value";
    private static final String PATH = "path";

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
        if (!entitlementsAndCacheService.isValidAcl(headers, valueSet)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid ACLs", "Invalid ACLs provided in acl path.");
        }
    }

    @Override
    public void validateLegalTags(JsonPatch jsonPatch) {
        Set<String> valueSet = getValueSet(jsonPatch, "/legal");
        legalService.validateLegalTags(valueSet);
    }

    private Set<String> getValueSet(JsonPatch jsonPatch, String path) {
        Set<String> valueSet = new HashSet<>();
        StreamSupport.stream(mapper.convertValue(jsonPatch, JsonNode.class).spliterator(), false)
                .filter(pathStartsWith(path))
                .forEach(operation -> {
                    JsonNode nodeValue = operation.get(VALUE);
                    if (nodeValue.getClass() == ArrayNode.class) {
                        StreamSupport.stream(mapper.convertValue(nodeValue, ArrayNode.class).spliterator(), false)
                                .map(this::removeExtraQuotes)
                                .forEach(valueSet::add);
                    } else if (nodeValue.getClass() == TextNode.class) {
                        valueSet.add(removeExtraQuotes(nodeValue));
                    }
                });

        return valueSet;
    }
    private Predicate<JsonNode> pathStartsWith(String path) {
        return operation -> removeExtraQuotes(operation.get(PATH)).startsWith(path);
    }

    private String removeExtraQuotes(JsonNode jsonNode) {
        return jsonNode.toString().replace("\"", "");
    }

}
