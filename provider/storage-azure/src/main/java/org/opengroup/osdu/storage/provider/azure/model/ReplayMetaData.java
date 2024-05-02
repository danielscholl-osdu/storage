package org.opengroup.osdu.storage.provider.azure.model;

import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.storage.request.ReplayFilter;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReplayMetaData {

    @Id
    private String id;

    @PartitionKey
    private String replayId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String kind;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String operation;

    private Long totalRecords;

    private Date startedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ReplayFilter filter;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long processedRecords;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String state;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String elapsedTime;
}
