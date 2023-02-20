package org.opengroup.osdu.storage.validation.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.stereotype.Component;

@Component
public class PatchInputValidatorImpl implements PatchInputValidator {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void validateAcls(JsonPatch jsonPatch) {
        //TODO: apply validation logic similar to MetadataPatchValidator.validateAcls()
    }

    @Override
    public void validateLegalTags(JsonPatch jsonPatch) {
        //TODO: apply validation logic similar to MetadataPatchValidator.validateLegalTags()
    }

    @Override
    public void validateTags(JsonPatch jsonPatch) {
        //TODO: apply validation logic similar to MetadataPatchValidator.validateTags()
    }
}
