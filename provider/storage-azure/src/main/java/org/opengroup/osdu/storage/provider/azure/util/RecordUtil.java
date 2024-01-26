package org.opengroup.osdu.storage.provider.azure.util;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;

@Component
public class RecordUtil {

    @Value("${record-id.max.length}")
    private Integer recordIdMaxLength;
    private static final Pattern endingCharacterPattern = Pattern.compile(".*[\\.\\\\\\/]$");
    private static final String UNSUPPORTED_CHARACTER_ERROR_MESSAGE = "RecordId values ending in dot (.), backslash (\\), or forward slash (/) not allowed";
    private static final String MAX_LENGTH_ERROR_MESSAGE = "RecordId values which are exceeded 100 symbols temporarily not allowed";

    public void validateIds(List<String> inputRecords) {
        if (inputRecords.stream().filter(Objects::nonNull)
                .anyMatch(id -> id.length() > recordIdMaxLength)) {

            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", MAX_LENGTH_ERROR_MESSAGE);
        }
        List<String> recordIds = inputRecords.stream().filter(Objects::nonNull).toList();
        for (String record : recordIds) {
            Matcher recordIdMatcher = endingCharacterPattern.matcher(record);
            boolean matchFound = recordIdMatcher.find();
            if (matchFound) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", UNSUPPORTED_CHARACTER_ERROR_MESSAGE);
            }
        }
    }

    public String getKindForVersion(RecordMetadata recordMetadata, String version) {
        String gcsVersionPath =
                recordMetadata.getGcsVersionPaths()
                        .stream()
                        .filter(isGcsVersionPathEndsWith(version))
                        .findFirst()
                        .orElseThrow(() -> throwVersionNotFound(recordMetadata.getId(), version));

        return gcsVersionPath.split("/")[0];
    }

    private static Predicate<String> isGcsVersionPathEndsWith(String version) {
        return gcsVersionPath -> isNoneBlank(gcsVersionPath) && gcsVersionPath.endsWith("/" + version);
    }

    private AppException throwVersionNotFound(String id, String version) {
        String errorMessage = String.format("The version %s can't be found for record %s", version, id);
        throw new AppException(HttpStatus.SC_NOT_FOUND, "Version not found", errorMessage);
    }
}
