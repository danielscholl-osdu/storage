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

    @Builder.Default
    private List<String> notFoundRecordIds = new ArrayList<>();
    @Builder.Default
    private List<String> unAuthorizedRecordIds = new ArrayList<>();
    @Builder.Default
    private List<String> lockedRecordIds = new ArrayList<>();
    @Builder.Default
    private List<String> failedRecordIds = new ArrayList<>();
}
