package org.opengroup.osdu.storage.validation.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.jsonpatch.JsonPatch;

public interface PatchInputValidator {

    //TODO: Do we need duplicates validator, or is it handled automatically by JsonPatch.apply()

    void validateAcls(JsonPatch jsonPatch);

    void validateLegalTags(JsonPatch jsonPatch);

    void validateTags(JsonPatch jsonPatch);
}
