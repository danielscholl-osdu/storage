package org.opengroup.osdu.storage.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opengroup.osdu.storage.validation.impl.VersionIdsValidator;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_LATEST_VERSION;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_VERSION_IDS_SIZE;

class VersionIdsValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    private VersionIdsValidator sut;

    @BeforeEach
    public void setup() {
        sut = new VersionIdsValidator();
    }

    @Test
    void should_validateVersionIds_forDefaultValue() {

        assertTrue(sut.isValid("", context));
        assertTrue(sut.isValid(null, context));

    }

    @Test
    void should_validateVersionIds_forVersionIdsPattern() {

        assertTrue(sut.isValid("1,2,3,4", context));
        assertFalse(sut.isValid("1, ,3, ", context));
        assertFalse(sut.isValid("1,2,,4", context));
        assertFalse(sut.isValid("1,3,", context));
        assertFalse(sut.isValid("1,3,-1", context));
        assertFalse(sut.isValid("1,two,3", context));

    }

    @Test
    void shouldThrowException_whenVersionIdsSizeIsGreaterThanAllowedValue() {
        int versionIdCount = 51;
        String versionIds = Stream.iterate(101, n -> n + 1)
                .limit(versionIdCount)
                .map(n -> String.valueOf(n)).collect(Collectors.joining(","));
        String expectedMessage = String.format(INVALID_VERSION_IDS_SIZE, versionIdCount);

        RequestValidationException requestValidationException = assertThrows( RequestValidationException.class, () ->
            VersionIdsValidator.validateVersionIdsSize(versionIds));
        assertEquals(expectedMessage, requestValidationException.getMessage());

    }

    @Test
    void shouldThrowException_whenVersionIdsHasLatestRecordVersion() {
        String versionIds = "1,2,3,4";
        Long latestVersion = 4l;
        String expectedMessage = String.format(INVALID_VERSION_IDS_FOR_LATEST_VERSION, latestVersion);

        RequestValidationException requestValidationException = assertThrows( RequestValidationException.class, () ->
                VersionIdsValidator.validateForLatestVersion(versionIds, latestVersion));
        assertEquals(expectedMessage, requestValidationException.getMessage());

    }

    @Test
    void shouldThrowException_whenVersionIdsHasNonExistingRecordVersions() {
        String versionPathPrefix = "kind1" + "/" + "recordID1" + "/";
        List<String> existingRecordVersionPaths = Stream.of("1", "2","3","4").map(version -> versionPathPrefix + version).toList();
        String versionIds = "1,2,5,7";
        String nonExistingVersions = "5,7";
        String expectedMessage = String.format(INVALID_VERSION_IDS_FOR_NON_EXISTING_VERSIONS, nonExistingVersions);

        RequestValidationException requestValidationException = assertThrows( RequestValidationException.class, () ->
                VersionIdsValidator.validateForNonExistingRecordVersions(versionIds, existingRecordVersionPaths));
        assertEquals(expectedMessage, requestValidationException.getMessage());

    }
}
