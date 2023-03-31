// Copyright 2017-2023, Schlumberger
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

package org.opengroup.osdu.storage.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import static org.opengroup.osdu.core.common.model.storage.SwaggerDoc.FETCH_RECORD_ID_LIST;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Represents a model for Record Query Patch", example = "{ \"ids\": [\"common:work-product-component--wellLog:123456\"] }")
public class RecordQueryPatch {
    @ArraySchema(arraySchema = @Schema(implementation = String.class, requiredMode = Schema.RequiredMode.REQUIRED, description = FETCH_RECORD_ID_LIST))
    private List<String> ids;
}
