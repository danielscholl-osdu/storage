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

package org.opengroup.osdu.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.validation.ValidateCollaborationContext;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayStatusResponse;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.opengroup.osdu.storage.service.replay.ReplayService;
import org.opengroup.osdu.storage.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Optional;
import java.util.UUID;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
@RestController
@RequestMapping("replay")
@RequestScope
@Validated
public class ReplayApi {

    @Autowired
    private ReplayService replayService;

    @Autowired
    private CollaborationContextFactory collaborationContextFactory;

    @Operation(summary = "${replayApi.getReplayStatus.summary}", description = "${replayApi.getReplayStatus.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = {"replay"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status of the replay", content = {@Content(schema = @Schema(implementation = ReplayStatusResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @GetMapping(value = "/status/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationFilter.hasRole('" + Role.USER_OPS + "')")
    public ResponseEntity<ReplayStatusResponse> getReplayStatus(@PathVariable("id") String id) {

        return new ResponseEntity<>(replayService.getReplayStatus(id), HttpStatus.OK);
    }

    @Operation(summary = "${replayApi.triggerReplay.summary}", description = "${replayApi.triggerReplay.description}",
            security = {@SecurityRequirement(name = "Authorization")}, tags = {"replay"})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Replay request is accepted", content = {@Content(schema = @Schema(implementation = ReplayResponse.class))}),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "403", description = "Forbidden", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {@Content(schema = @Schema(implementation = AppError.class))}),
            @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {@Content(schema = @Schema(implementation = AppError.class))})
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@authorizationFilter.hasRole('" + Role.USER_OPS + "')")
    public ResponseEntity<ReplayResponse> triggerReplay(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false)
                                                        @jakarta.validation.Valid @ValidateCollaborationContext String collaborationDirectives,
                                                        @Valid @RequestBody ReplayRequest replayRequest) {

        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
        if (collaborationContext.isPresent())
            throw new AppException(
                    org.apache.http.HttpStatus.SC_NOT_IMPLEMENTED,
                    "Collaboration feature not implemented for Replay API.",
                    "Collaboration feature is not yet supported for the Replay API.");

        replayRequest.setReplayId(UUID.randomUUID().toString());
        ReplayResponse response = replayService.handleReplayRequest(replayRequest);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }
}
