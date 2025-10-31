/*
 * Copyright 2025 bp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;

import java.util.Map;

/**
 * DTO for merge patch operations on records.
 * This supports partial updates using application/merge-patch+json content type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Record merge patch request", 
        example = """
        {
          "acl": {
            "viewers": ["data.viewer@tenant.com"],
            "owners": ["data.owner@tenant.com"]
          },
          "legal": {
            "legaltags": ["tenant-public-usa-dataset-1"],
            "otherRelevantDataCountries": ["US"]
          },
          "data": {
            "wellName": "Updated Well Name",
            "status": "active"
          },
          "tags": {
            "environment": "production"
          }
        }""")
public class RecordMergePatchRequest {

    @Schema(description = "Record kind")
    private String kind;

    @Schema(description = "Access Control List for the record")
    private Acl acl;

    @Schema(description = "Legal information for the record")
    private Legal legal;

    @Schema(description = "Record data")
    private Map<String, Object> data;

    @Schema(description = "Record tags as key-value pairs")
    private Map<String, String> tags;

    @Schema(description = "Record ancestry data")
    private RecordAncestry ancestry;

    @Schema(description = "Soft delete flag - set to false to undelete", example = "false")
    private Boolean deleted;

    @Schema(description = "Deletion timestamp - set to null to undelete")
    private String deletedAt;
}
