package org.opengroup.osdu.storage.response;

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
public class ReplayStatusResponse {

    private String replayId;

    private String operation;

    private Long totalRecords;

    private Date startedAt;

    private String elapsedTime;

    private Long processedRecords;

    private String overallState;

    private ReplayFilter filter;

    private List<ReplayStatus> status;
}
