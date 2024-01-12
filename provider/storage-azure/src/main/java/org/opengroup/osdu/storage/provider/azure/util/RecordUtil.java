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

import static org.apache.commons.lang3.StringUtils.isNoneBlank;

@Component
public class RecordUtil {

    @Value("${record-id.max.length}")
    private Integer recordIdMaxLength;
    private static final java.util.regex.Pattern endingCharacterPattern = java.util.regex.Pattern.compile(".*[\\.\\\\\\/]$");

    public void validateIds(List<String> inputRecords) {
        if (inputRecords.stream().filter(Objects::nonNull)
                .anyMatch(id -> id.length() > recordIdMaxLength)) {
            String msg = "RecordId values which are exceeded 100 symbols temporarily not allowed";
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", msg);
        }

        for (String record : inputRecords) {
            Matcher recordIdMatcher = endingCharacterPattern.matcher(record);
            boolean matchFound = recordIdMatcher.find();
            if (matchFound) {
                String msg = "RecordId values ending in dot, backslash, or forward slash not allowed";
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", msg);
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
