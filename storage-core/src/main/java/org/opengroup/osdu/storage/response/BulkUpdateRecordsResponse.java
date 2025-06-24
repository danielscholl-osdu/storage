package org.opengroup.osdu.storage.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkUpdateRecordsResponse {

    private Integer recordCount;

    private List<String> recordIds;

    private List<String> notFoundRecordIds;

    private List<String> unAuthorizedRecordIds;

    private List<String> lockedRecordIds;
}
