package org.opengroup.osdu.storage.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.opengroup.osdu.storage.dto.ReplayStatus;

import java.util.Date;
import java.util.List;


@AllArgsConstructor
@Data
@NoArgsConstructor
@Schema(description = "Response object containing the status of a replay operation.")
public class ReplayStatusResponse {

    @Schema(description = "Unique identifier of the replay operation.", example = "f506be08-b51f-4cdd-a9ee-d9454ad51600")
    private String replayId;

    @Schema(description = "The operation type that was requested.", example = "reindex")
    private String operation;

    @Schema(description = "Total number of records to be processed.")
    private Long totalRecords;

    @Schema(description = "Timestamp when the replay operation started.")
    private Date startedAt;

    @Schema(description = "Human-readable elapsed time since the operation started.", example = "0h 5m 30s")
    private String elapsedTime;

    @Schema(description = "Number of records processed so far.")
    private Long processedRecords;

    @Schema(description = "Overall state of the replay operation.", example = "IN_PROGRESS")
    private String overallState;

    @Schema(description = "Filter that was applied to the replay operation.")
    private ReplayFilter filter;

    @Schema(description = "Per-kind status breakdown of the replay operation.")
    private List<ReplayStatus> status;
}
