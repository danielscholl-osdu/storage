package org.opengroup.osdu.storage.validation.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.fge.jsonpatch.JsonPatch;

public interface PatchInputValidator {

    //TODO: Do we need duplicates validator, or is it handled automatically by JsonPatch.apply()

    void validateDuplicates(JsonPatch jsonPatch);

    void validateAcls(JsonPatch jsonPatch);

    void validateLegalTags(JsonPatch jsonPatch);

    void validateKind(JsonPatch jsonPatch);

    void validateAncestry(JsonPatch jsonPatch);

}
