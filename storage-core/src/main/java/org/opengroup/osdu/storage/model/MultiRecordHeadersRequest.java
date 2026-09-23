/*
 *  Copyright 2020-2026 Google LLC
 *  Copyright 2020-2026 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.model;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiRecordHeadersRequest {

    @NotEmpty(message = "Record list cannot be empty")
    @Size(min = 1, max = 1000, message = "Record list size must be between 1 and 1000")
    private List<String> records;

    @ArraySchema(
            schema = @Schema(
                    type = "string",
                    allowableValues = {
                            RecordHeadersDTO.ATTRIBUTE_VERSION,
                            RecordHeadersDTO.ATTRIBUTE_KIND,
                            RecordHeadersDTO.ATTRIBUTE_ACL,
                            RecordHeadersDTO.ATTRIBUTE_LEGAL,
                            RecordHeadersDTO.ATTRIBUTE_ANCESTRY,
                            RecordHeadersDTO.ATTRIBUTE_TAGS,
                            RecordHeadersDTO.ATTRIBUTE_CREATE_USER,
                            RecordHeadersDTO.ATTRIBUTE_CREATE_TIME,
                            RecordHeadersDTO.ATTRIBUTE_MODIFY_USER,
                            RecordHeadersDTO.ATTRIBUTE_MODIFY_TIME
                    }
            ),
            arraySchema = @Schema(
                    description = "Filter/projection: list of record header attributes to return. If specified, only these attributes (plus 'id') will be returned. If omitted or empty, all record header fields are returned."
            ),
            uniqueItems = true,
            maxItems = 10
    )
    @Size(max = 10, message = "Attributes list size cannot exceed 10")
    private List<@Pattern(
            regexp = "^(version|kind|acl|legal|ancestry|tags|createUser|createTime|modifyUser|modifyTime)$",
            flags = {Pattern.Flag.CASE_INSENSITIVE},
            message = "attributes must be one of: version, kind, acl, legal, ancestry, tags, createUser, createTime, modifyUser, modifyTime"
    ) String> attributes;
}
