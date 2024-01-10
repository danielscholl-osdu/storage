/*
 Copyright 2002-2023 Google LLC
 Copyright 2002-2023 EPAM Systems, Inc

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

package org.opengroup.osdu.storage.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.security.PermitAll;

@RestController
@RequestMapping
@Tag(name = "health-check-api", description = "Health Check API")
public class HealthCheckApi {

  @Operation(
      summary = "${healthCheckApi.livenessCheck.summary}",
      description = "${healthCheckApi.livenessCheck.description}",
      tags = {"health-check-api"})
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "OK",
            content = {@Content(schema = @Schema(implementation = String.class))}),
        @ApiResponse(
            responseCode = "502",
            description = "Bad Gateway",
            content = {@Content(schema = @Schema(implementation = AppError.class))}),
        @ApiResponse(
            responseCode = "503",
            description = "Service Unavailable",
            content = {@Content(schema = @Schema(implementation = AppError.class))})
      })
  @PermitAll
  @GetMapping("/liveness_check")
  ResponseEntity<String> livenessCheck() {
    return new ResponseEntity<>("Storage service is alive.", HttpStatus.OK);
  }
}
