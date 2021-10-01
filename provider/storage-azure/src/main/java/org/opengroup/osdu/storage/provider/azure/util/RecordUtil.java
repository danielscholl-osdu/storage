package org.opengroup.osdu.storage.provider.azure.util;

import static org.apache.commons.lang3.StringUtils.isNoneBlank;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class RecordUtil {

    @Value("${record-id.max.length}")
    private Integer recordIdMaxLength;

    public void validateIds(List<String> inputRecords) {
        if (inputRecords.stream().filter(Objects::nonNull)
                .anyMatch(id -> id.length() > recordIdMaxLength)) {
            String msg = "RecordId values which are exceeded 100 symbols temporarily not allowed";
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid id", msg);
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
        throw new AppException(500, "Version not found", errorMessage);
    }
}