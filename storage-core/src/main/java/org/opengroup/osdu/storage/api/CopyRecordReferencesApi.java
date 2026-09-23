/*
 *    Copyright (c) 2024. EPAM Systems, Inc
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.opengroup.osdu.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.model.CopyRecordReferencesModel;
import org.opengroup.osdu.storage.service.CopyRecordReferencesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("records")
@Tag(name = "records", description = "Copying record references management operations")
@Validated
public class CopyRecordReferencesApi {

  @Autowired
  private CopyRecordReferencesService copyRecordReferencesService;

  @Operation(summary = "${recordReferencesApi.copyRecordReferences.summary}", description = "${recordReferencesApi.copyRecordReferences.description}",
      security = {@SecurityRequirement(name = "Authorization")}, tags = {"records"})
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Record references copied successfully.", content = {
          @Content(schema = @Schema(implementation = CopyRecordReferencesModel.class))}),
      @ApiResponse(responseCode = "400", description = "Invalid record ids provided.", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "401", description = "Unauthorized", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "403", description = "User not authorized to perform the action.", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "404", description = "Records not found in the source.", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "409", description = "One or more references already exist in the target namespace.", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "500", description = "Internal Server Error", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "502", description = "Bad Gateway", content = {
          @Content(schema = @Schema(implementation = AppError.class))}),
      @ApiResponse(responseCode = "503", description = "Service Unavailable", content = {
          @Content(schema = @Schema(implementation = AppError.class))})
  })
  @PutMapping(path = "/copy", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(
      "@authorizationFilter.hasRole('" + StorageRole.ADMIN + "')")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<CopyRecordReferencesModel> copyRecordReferencesBetweenNamespaces(
      @Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration")
      @Valid String collaborationDirectives,
      @Parameter(description = "Record references with target namespace") @RequestBody @Valid CopyRecordReferencesModel request) {

    return new ResponseEntity<>(
        copyRecordReferencesService.copyRecordReferences(request, collaborationDirectives),
        HttpStatus.OK);
  }
}
