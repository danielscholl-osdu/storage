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

package org.opengroup.osdu.storage.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for triggering a replay operation.")
public class ReplayRequest {

    @Schema(description = "Optional client-supplied replay identifier. If omitted, the server generates one.", example = "f506be08-b51f-4cdd-a9ee-d9454ad51600")
    private String replayId;

    @NotEmpty(message = "Operation field is required. The valid operations are: 'replay', 'reindex'.")
    @Schema(description = "The operation to perform. Valid values: 'replay', 'reindex'.", example = "reindex", requiredMode = Schema.RequiredMode.REQUIRED)
    private String operation;

    @Valid
    @Schema(description = "Optional filter to restrict the replay to specific kinds.")
    private ReplayFilter filter;
}
