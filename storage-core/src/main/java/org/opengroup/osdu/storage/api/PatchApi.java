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

package org.opengroup.osdu.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.validation.ValidateCollaborationContext;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.BulkUpdateRecordService;
import org.opengroup.osdu.storage.service.PatchRecordsService;
import org.opengroup.osdu.storage.util.CollaborationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import org.opengroup.osdu.storage.response.PatchRecordsResponse;

import jakarta.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("records")
@Tag(name = "records", description = "Records management operations")
@RequestScope
@Validated
public class PatchApi {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private BulkUpdateRecordService bulkUpdateRecordService;

    @Autowired
    private PatchRecordsService patchRecordsService;

    @Autowired
    private CollaborationContextFactory collaborationContextFactory;

    @Operation(summary = "${patchApi.updateRecordsMetadata.summary}", description = "${patchApi.updateRecordsMetadata.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = {"records"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Records updated successfully.", content = {@Content(schema = @Schema(implementation = BulkUpdateRecordsResponse.class))}),
            @ApiResponse(responseCode = "206", description = "Records updated successful partially.", content = {@Content(schema = @Schema(implementation = BulkUpdateRecordsResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action.", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
    public ResponseEntity<BulkUpdateRecordsResponse> updateRecordsMetadata(@Parameter(description = "x-collaboration") @RequestHeader(name = CollaborationFilter.X_COLLABORATION_HEADER_NAME, required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
                                                                           @Parameter(description = "Records to be updated") @RequestBody @Valid RecordBulkUpdateParam recordBulkUpdateParam) {
        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
        BulkUpdateRecordsResponse response = this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.headers.getUserEmail(), collaborationContext);
        if (!response.getLockedRecordIds().isEmpty() || !response.getNotFoundRecordIds().isEmpty() || !response.getUnAuthorizedRecordIds().isEmpty()) {
            return new ResponseEntity<>(response, HttpStatus.PARTIAL_CONTENT);
        } else {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    @Operation(summary = "${patchApi.patchRecords.summary}", description = "${patchApi.patchRecords.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = {"records"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Records updated successfully.", content = {@Content(schema = @Schema(implementation = PatchRecordsResponse.class))}),
            @ApiResponse(responseCode = "206", description = "Records updated successful partially.", content = {@Content(schema = @Schema(implementation = PatchRecordsResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "User not authorized to perform the action.", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "404", description = "Not Found", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PatchMapping(consumes = "application/json-patch+json", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
    public ResponseEntity<PatchRecordsResponse> patchRecords(@Parameter(description = "x-collaboration") @RequestHeader(name = CollaborationFilter.X_COLLABORATION_HEADER_NAME, required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
                                                             @Parameter(description = "Records to be patched") @RequestBody @Valid PatchRecordsRequestModel patchRecordsRequest) {
        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
        PatchRecordsResponse response = this.patchRecordsService.patchRecords(patchRecordsRequest.getQuery().getIds(), patchRecordsRequest.getOps(), this.headers.getUserEmail(), collaborationContext);
        if (!response.getNotFoundRecordIds().isEmpty() || !response.getFailedRecordIds().isEmpty()) {
            return new ResponseEntity<>(response, HttpStatus.PARTIAL_CONTENT);
        } else {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }
}
