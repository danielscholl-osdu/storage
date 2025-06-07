package org.opengroup.osdu.storage.validation.impl;

import com.google.common.base.Strings;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.opengroup.osdu.storage.validation.RequestValidationException;
import org.opengroup.osdu.storage.validation.api.ValidVersionIds;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.opengroup.osdu.storage.util.RecordConstants.MAX_VERSION_IDS_NUMBER;
import static org.opengroup.osdu.storage.util.RecordConstants.REGEX_VERSION_IDS;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_LATEST_VERSION;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_SIZE;

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
                .filter(versionId -> existingRecordVersionPaths.parallelStream().noneMatch(paths -> paths.contains(versionId)))
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


}
