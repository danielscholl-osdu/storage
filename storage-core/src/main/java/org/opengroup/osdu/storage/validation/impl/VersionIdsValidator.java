package org.opengroup.osdu.storage.validation.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.opengroup.osdu.storage.util.RecordConstants.MAX_VERSION_IDS_NUMBER;
import static org.opengroup.osdu.storage.util.RecordConstants.REGEX_VERSION_IDS;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_LATEST_VERSION;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_SIZE;
import org.opengroup.osdu.storage.validation.api.ValidVersionIds;

import com.google.common.base.Strings;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VersionIdsValidator implements ConstraintValidator<ValidVersionIds, String> {

    public static void validateVersionIdsSize(String versionIds) {
        List<String> versionIdList = Arrays.stream(versionIds.split(",")).toList();
        boolean isInvalidVersionIdsSize = (versionIdList.size() > MAX_VERSION_IDS_NUMBER);
        if (isInvalidVersionIdsSize) {
            throw RequestValidationException.builder()
                    .message(String.format(INVALID_VERSION_IDS_SIZE, versionIdList.size()))
                    .build();
        }
    }

    public static void validateForLatestVersion(String versionIds, Long latestVersion) {
        if (versionIds.contains(String.valueOf(latestVersion))) {
            throw RequestValidationException.builder()
                    .message(String.format(INVALID_VERSION_IDS_FOR_LATEST_VERSION, latestVersion))
                    .build();
        }
    }

    public static void validateForNonExistingRecordVersions(String versionIds, List<String> existingRecordVersionPaths) {
        List<String> versionIdList = Arrays.stream(versionIds.split(",")).toList();
        String nonExistingVersions = versionIdList.stream()
                .filter(versionId -> existingRecordVersionPaths.stream().noneMatch(versionPath -> versionPathHasVersionId(versionPath, versionId)))
                .collect(Collectors.joining(","));
        if (!nonExistingVersions.isEmpty()) {
            throw RequestValidationException.builder()
                    .message(String.format(INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS, nonExistingVersions))
                    .build();
        }
    }

    public static void validate(String versionIds, Long latestVersion, List<String> existingRecordVersionPaths) {
        validateVersionIdsSize(versionIds);
        validateForLatestVersion(versionIds, latestVersion);
        validateForNonExistingRecordVersions(versionIds, existingRecordVersionPaths);
    }

    @Override
    public boolean isValid(String versionIds, ConstraintValidatorContext constraintValidatorContext) {
        return (Strings.isNullOrEmpty(versionIds) || versionIds.matches(REGEX_VERSION_IDS));
    }

    /**
     * Helper method to check if the version path has the given version id.
     * 
     * Note: If the version path is not in the expected format, then we cannot be sure
     *       that the last part of it is the version id, so we will return false.
     *
     * @param versionPath the version path to check
     * @param versionId the given version id
     * @return true if the version path has the given version id, false otherwise
     */
    private static boolean versionPathHasVersionId(String versionPath, String versionId) {
        String[] versionPathParts = versionPath.split("/");

        // A valid version path must have exactly 3 parts: <kind>/<record-id>/<version-id>
        // If it does not have 3 parts, it cannot have a valid version id.
        if (versionPathParts.length != 3) {
            return false;
        }

        String versionIdFromVersionPath = versionPathParts[versionPathParts.length - 1];
        return versionIdFromVersionPath.equals(versionId);
    }
}
