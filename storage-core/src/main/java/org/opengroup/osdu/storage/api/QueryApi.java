// Copyright 2017-2019, Schlumberger
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

import io.swagger.v3.oas.annotations.Hidden;
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
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.storage.validation.ValidKind;
import org.opengroup.osdu.core.common.model.validation.ValidateCollaborationContext;
import org.opengroup.osdu.storage.di.SchemaEndpointsConfig;
import org.opengroup.osdu.storage.service.BatchService;
import org.opengroup.osdu.storage.util.EncodeDecode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import javax.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("query")
@Tag(name = "query", description = "Querying Records operations")
@RequestScope
@Validated
public class QueryApi {

	@Autowired
	private BatchService batchService;

	@Autowired
	private EncodeDecode encodeDecode;

	@Autowired
	private SchemaEndpointsConfig schemaEndpointsConfig;
	@Autowired
	private CollaborationContextFactory collaborationContextFactory;

	@Operation(summary = "${queryApi.getAllRecords.summary}", description = "${queryApi.getAllRecords.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "query" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Record Ids retrieved successfully.", content = { @Content(schema = @Schema(implementation = DatastoreQueryResult.class)) }),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Kind or cursor not found.", content = {@Content(schema = @Schema(implementation = AppError.class))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@GetMapping(value = "/records", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.ADMIN + "')")
	public ResponseEntity<DatastoreQueryResult> getAllRecords(
			@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
			@Parameter(description = "Cursor") @RequestParam(required = false) String cursor,
			@Parameter(description = "Page Size", example = "10") @RequestParam(required = false) Integer limit,
			@Parameter(description = "Filter Kind", example = "tenant1:public:well:1.0.2") @RequestParam @ValidKind String kind) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		DatastoreQueryResult result = this.batchService.getAllRecords(encodeDecode.deserializeCursor(cursor), kind, limit, collaborationContext);
		result.setCursor(encodeDecode.serializeCursor(result.getCursor()));
		return new ResponseEntity<DatastoreQueryResult>(result, HttpStatus.OK);
	}


	@Operation(summary = "${queryApi.getRecords.summary}", description = "${queryApi.getRecords.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "query" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Fetch multiple records successfully.", content = { @Content(schema = @Schema(implementation = MultiRecordInfo.class)) }),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@PostMapping(value = "/records", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<MultiRecordInfo> getRecords(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false)
													  @Valid @ValidateCollaborationContext String collaborationDirectives,
													  @Parameter(description = "Record ids") @Valid @RequestBody MultiRecordIds ids) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		return new ResponseEntity<MultiRecordInfo>(this.batchService.getMultipleRecords(ids, collaborationContext), HttpStatus.OK);
	}

	/**
	 * New fetch records Api, allows maximum 20 records per request and customized header to do conversion.
	 * @param ids id of records to be fetched
	 * @return valid records
	 */
	@Operation(summary = "${queryApi.fetchRecords.summary}", description = "${queryApi.fetchRecords.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "query" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Fetch multiple records successfully.", content = { @Content(schema = @Schema(implementation = MultiRecordResponse.class)) }),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@PostMapping(value = "/records:batch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<MultiRecordResponse> fetchRecords(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false)
															@Valid @ValidateCollaborationContext String collaborationDirectives,
															@Parameter(description = "Record ids") @Valid @RequestBody MultiRecordRequest ids) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		return new ResponseEntity<MultiRecordResponse>(this.batchService.fetchMultipleRecords(ids, collaborationContext), HttpStatus.OK);
	}

	// This endpoint is deprecated as of M6, replaced by schema service. In M7 this endpoint will be deleted
	@Hidden
	@GetMapping(value = "/kinds", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<DatastoreQueryResult> getKinds(@RequestParam(required = false) String cursor,
														 @RequestParam(required = false) Integer limit) {
		DatastoreQueryResult result = this.batchService.getAllKinds(encodeDecode.deserializeCursor(cursor), limit);
		result.setCursor(encodeDecode.serializeCursor(result.getCursor()));
		return new ResponseEntity<DatastoreQueryResult>(result, HttpStatus.OK);
	}
}
