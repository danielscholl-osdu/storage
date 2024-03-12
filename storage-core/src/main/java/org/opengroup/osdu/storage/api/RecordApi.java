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
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.core.common.model.validation.ValidateCollaborationContext;
import org.opengroup.osdu.storage.exception.DeleteRecordsException;
import org.opengroup.osdu.storage.mapper.CreateUpdateRecordsResponseMapper;
import org.opengroup.osdu.storage.response.CreateUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.IngestionService;
import org.opengroup.osdu.storage.service.QueryService;
import org.opengroup.osdu.storage.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("records")
@Tag(name = "records", description = "Records management operations")
@RequestScope
@Validated
public class RecordApi {

	@Autowired
	private DpsHeaders headers;

	@Autowired
	private IngestionService ingestionService;

	@Autowired
	private QueryService queryService;

	@Autowired
	private RecordService recordService;

	@Autowired
	private CreateUpdateRecordsResponseMapper createUpdateRecordsResponseMapper;

	@Autowired
	private CollaborationContextFactory collaborationContextFactory;

	@Operation(summary = "${recordApi.createOrUpdateRecords.summary}", description = "${recordApi.createOrUpdateRecords.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "201", description = "Records created and/or updated successfully.", content = { @Content(schema = @Schema(implementation = CreateUpdateRecordsResponse.class)) }),
			@ApiResponse(responseCode = "400", description = "Invalid record format.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "User not authorized to perform the action.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Invalid acl group.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	@ResponseStatus(HttpStatus.CREATED)
	public CreateUpdateRecordsResponse createOrUpdateRecords(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false)
															 @Valid @ValidateCollaborationContext String collaborationDirectives,
															 @Parameter(description = "Skip duplicates when updating records with the same value.") @RequestParam(required = false) boolean skipdupes,
															 @Parameter(description = "Records to be created/updated") @RequestBody @Valid @NotEmpty @Size(max = 500, message = ValidationDoc.RECORDS_MAX) List<Record> records) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		TransferInfo transfer = ingestionService.createUpdateRecords(skipdupes, records, headers.getUserEmail(), collaborationContext);
		return createUpdateRecordsResponseMapper.map(transfer, records);
	}

	@Operation(summary = "${recordApi.getLatestRecordVersion.summary}", description = "${recordApi.getLatestRecordVersion.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Record retrieved successfully.", content = { @Content(schema = @Schema(implementation = String.class)) }),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Record not found.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<String> getLatestRecordVersion(@Parameter(description = "x-collaboration")
														 @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
														 @Parameter(description = "Record id", example = "tenant1:well:123456789") @PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
																 message = ValidationDoc.INVALID_RECORD_ID) String id,
														 @Parameter(description = "Filter attributes to restrict the returned fields of the record. " +
																 " Usage: data.{record-data-field-name}.", example = "data.wellName") @RequestParam(name = "attribute", required = false) String[] attributes) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		return new ResponseEntity<String>(this.queryService.getRecordInfo(id, attributes, collaborationContext), HttpStatus.OK);
	}

	@Operation(summary = "${recordApi.purgeRecord.summary}", description = "${recordApi.purgeRecord.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Record purged successfully."),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Record not found.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@DeleteMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> purgeRecord(@Parameter(description = "x-collaboration")
											@RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
											@Parameter(description = "Record id", example = "tenant1:well:123456789") @PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
													message = ValidationDoc.INVALID_RECORD_ID) String id) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		this.recordService.purgeRecord(id, collaborationContext);
		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "${recordApi.deleteRecord.summary}", description = "${recordApi.deleteRecord.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "Record deleted successfully."),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Record not found.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@PostMapping(value = "/{id}:delete", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> deleteRecord(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
											 @Parameter(description = "Record id", example = "tenant1:well:123456789") @PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
													 message = ValidationDoc.INVALID_RECORD_ID) String id) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		this.recordService.deleteRecord(id, this.headers.getUserEmail(), collaborationContext);
		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "${recordApi.bulkDeleteRecords.summary}", description = "${recordApi.bulkDeleteRecords.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "204", description = "All records deleted successfully."),
			@ApiResponse(responseCode = "207", description = "Some of the records weren't deleted successfully.",  content = {@Content(schema = @Schema(implementation = DeleteRecordsException.class ))}),
			@ApiResponse(responseCode = "400", description = "Invalid id format",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Not Found",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> bulkDeleteRecords(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
												  @Parameter(description = "recordIds to be deleted") @RequestBody @NotEmpty @Size(max = 500, message = ValidationDoc.RECORDS_MAX) List<String> recordIds) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		this.recordService.bulkDeleteRecords(recordIds, this.headers.getUserEmail(), collaborationContext);
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}

	@Operation(summary = "${recordApi.getSpecificRecordVersion.summary}", description = "${recordApi.getSpecificRecordVersion.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Record retrieved successfully.", content = { @Content(schema = @Schema(implementation = String.class )) }),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Record id or version not found.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@GetMapping(value = "/{id}/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<String> getSpecificRecordVersion(@Parameter(description = "x-collaboration")
														   @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
														   @Parameter(description = "Record id", example = "tenant1:well:123456789") @PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
																   message = ValidationDoc.INVALID_RECORD_ID) String id,
														   @Parameter(description = "Record version", example = "123456789") @PathVariable("version") long version,
														   @Parameter(description = "Filter attributes to restrict the returned fields of the record. " +
																   " Usage: data.{record-data-field-name}.", example = "data.wellName")  @RequestParam(name = "attribute", required = false) String[] attributes) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		return new ResponseEntity<String>(this.queryService.getRecordInfo(id, version, attributes, collaborationContext), HttpStatus.OK);
	}

	@Operation(summary = "${recordApi.getRecordVersions.summary}", description = "${recordApi.getRecordVersions.description}",
			security = {@SecurityRequirement(name = "Authorization")}, tags = { "records" })
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Record versions retrieved successfully.", content = { @Content(schema = @Schema(implementation = RecordVersions.class )) }),
			@ApiResponse(responseCode = "400", description = "Bad Request",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "401", description = "Unauthorized", content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "403", description = "Forbidden",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "404", description = "Record id or version not found.",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "500", description = "Internal Server Error",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "502", description = "Bad Gateway",  content = {@Content(schema = @Schema(implementation = AppError.class ))}),
			@ApiResponse(responseCode = "503", description = "Service Unavailable",  content = {@Content(schema = @Schema(implementation = AppError.class ))})
	})
	@GetMapping(value = "/versions/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<RecordVersions> getRecordVersions(@Parameter(description = "x-collaboration") @RequestHeader(name = "x-collaboration", required = false) @Valid @ValidateCollaborationContext String collaborationDirectives,
															@Parameter(description = "Record id", example = "tenant1:well:123456789") @PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
																	message = ValidationDoc.INVALID_RECORD_ID) String id) {
		Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(collaborationDirectives);
		return new ResponseEntity<RecordVersions>(this.queryService.listVersions(id, collaborationContext), HttpStatus.OK);
	}
}
