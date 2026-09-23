// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Status of a replay operation for a specific record kind.")
public class ReplayStatus {

    @Schema(description = "Record kind being replayed.", example = "osdu:wks:master-data--Well:1.0.0")
    private String kind;

    @Schema(description = "Total number of records of this kind to be processed.")
    private Long totalRecords;

    @Schema(description = "Number of records of this kind processed so far.")
    private Long processedRecords;

    @Schema(description = "State of the replay for this kind.", example = "IN_PROGRESS")
    private String state;

    @Schema(description = "Timestamp when processing of this kind started.")
    private Date startedAt;

    @Schema(description = "Human-readable elapsed time for this kind.", example = "0h 2m 15s")
    private String elapsedTime;
}
