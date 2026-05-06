package org.opengroup.osdu.storage.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Response object returned when a replay operation is triggered.")
public class ReplayResponse {

    @Schema(description = "Unique identifier for the triggered replay operation.", example = "f506be08-b51f-4cdd-a9ee-d9454ad51600")
    private String replayId;
}
