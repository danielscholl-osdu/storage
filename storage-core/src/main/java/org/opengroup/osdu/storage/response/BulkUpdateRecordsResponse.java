package org.opengroup.osdu.storage.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Response object for bulk metadata update operations.")
public class BulkUpdateRecordsResponse {

    @Schema(description = "Number of records that were successfully updated.")
    private Integer recordCount;

    @Schema(description = "List of record ids that were successfully updated.")
    private List<String> recordIds;

    @Schema(description = "List of record ids that were not found.")
    private List<String> notFoundRecordIds;

    @Schema(description = "List of record ids that the caller is not authorized to update.")
    private List<String> unAuthorizedRecordIds;

    @Schema(description = "List of record ids that are currently locked and cannot be updated.")
    private List<String> lockedRecordIds;
}
