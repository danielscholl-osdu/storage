package org.opengroup.osdu.storage.response;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PatchRecordsResponse {

    private Integer recordCount;

    private List<String> recordIds;

    private List<String> notFoundRecordIds;

    private List<String> unAuthorizedRecordIds;

    @Builder.Default
    private List<String> lockedRecordIds = new ArrayList<>();

    private List<String> failedRecordIds;
}
