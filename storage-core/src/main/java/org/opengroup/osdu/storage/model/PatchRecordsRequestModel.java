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

import com.github.fge.jsonpatch.JsonPatch;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.SwaggerDoc;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.storage.validation.api.ValidBulkQueryPatch;
import org.opengroup.osdu.storage.validation.api.ValidJsonPatch;

import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PatchRecordsRequestModel {

    @ApiModelProperty(value = SwaggerDoc.RECORD_QUERY_CONDITION, required = true)
    @NotNull(message = ValidationDoc.RECORD_QUERY_CONDITION_NOT_EMPTY)
    @ValidBulkQueryPatch
    private RecordQueryPatch query;

    @ApiModelProperty(value = org.opengroup.osdu.storage.swagger.SwaggerDoc.PATCH_RECORD_OPERATIONS, required = true)
    @NotNull(message = org.opengroup.osdu.storage.validation.ValidationDoc.PATCH_RECORD_OPERATIONS_NOT_EMPTY)
    @ValidJsonPatch
    private JsonPatch ops;
}
