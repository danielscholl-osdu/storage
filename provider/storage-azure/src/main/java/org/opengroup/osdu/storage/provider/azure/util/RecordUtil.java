package org.opengroup.osdu.storage.provider.azure.util;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;

@Component
public class RecordUtil {

    @Value("${record-id.max.length}")
    private Integer recordIdMaxLength;

    private static final String FORBIDDEN_CHARACTER = ".";
    private static final String MAX_LENGTH_ERROR_MESSAGE = "RecordId values which are exceeded 100 symbols temporarily not allowed";
    private static final String UNSUPPORTED_CHARACTER_ERROR_MESSAGE = "RecordId values ending in dot (.) and without dot (.) are not allowed on same request, please split records in separate requests";

    public void validateIds(List<String> inputRecords) {
        if (inputRecords.stream().filter(Objects::nonNull)
                .anyMatch(id -> id.length() > recordIdMaxLength)) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", MAX_LENGTH_ERROR_MESSAGE);
        }

        List<String> recordIds = inputRecords.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (recordIds.isEmpty()) {
            return;
        }
        for (String id : recordIds) {
            // In addition to dot (.), Azure Cloud Storage does not support trailing backslash (\), or forward slash (/) as well,
            // record-id with these characters is rejected by record-id validator in os-core-common lib.
            if (!id.endsWith(FORBIDDEN_CHARACTER)) {
                continue;
            }
            String idWithoutForbiddenCharacter = id.substring(0, id.length() - 1);
            if (recordIds.contains(idWithoutForbiddenCharacter)) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", UNSUPPORTED_CHARACTER_ERROR_MESSAGE);
            }
        }
    }

    public String getKindForVersion(RecordMetadata record, String version) {
        String versionPath =
                record.getGcsVersionPaths()
                        .stream()
                        .filter(path -> isNoneBlank(path) && path.contains(version))
                        .findFirst()
                        .orElseThrow(() -> throwVersionNotFound(record.getId(), version));

        return versionPath.split("/")[0];
    }

    private AppException throwVersionNotFound(String id, String version) {
        String errorMessage = String.format("The version %s can't be found for record %s", version, id);
        throw new AppException(HttpStatus.SC_NOT_FOUND, "Version not found", errorMessage);
    }
}
