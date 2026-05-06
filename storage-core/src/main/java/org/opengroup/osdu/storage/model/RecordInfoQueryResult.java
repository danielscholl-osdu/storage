package org.opengroup.osdu.storage.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Paginated query result containing a cursor and a list of results.")
public class RecordInfoQueryResult <T> {

    @Schema(description = "Cursor for fetching the next page of results. Null when there are no more results.")
    private String cursor;

    @Schema(description = "List of results for the current page.")
    private List<T> results;
}
