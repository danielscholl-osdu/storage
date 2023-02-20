package org.opengroup.osdu.storage.validation.impl;

import com.github.fge.jsonpatch.JsonPatch;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.stereotype.Component;

@Component
public class PatchInputValidatorImpl implements PatchInputValidator {

    @Override
    public void validateAcls(JsonPatch jsonPatch) {

    }

    @Override
    public void validateLegalTags(JsonPatch jsonPatch) {

    }

    @Override
    public void validateTags(JsonPatch jsonPatch) {

    }
}
