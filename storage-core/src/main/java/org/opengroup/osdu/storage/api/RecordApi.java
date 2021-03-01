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

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;
import org.opengroup.osdu.core.common.storage.IngestionService;
import org.opengroup.osdu.storage.mapper.CreateUpdateRecordsResponseMapper;
import org.opengroup.osdu.storage.response.CreateUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.QueryService;
import org.opengroup.osdu.storage.service.RecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.annotation.RequestScope;

@RestController
@RequestMapping("records")
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

	@PutMapping
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	@ResponseStatus(HttpStatus.CREATED)
	public CreateUpdateRecordsResponse createOrUpdateRecords(@RequestParam(required = false) boolean skipdupes,
			@RequestBody @Valid @NotEmpty @Size(max = 500, message = ValidationDoc.RECORDS_MAX) List<Record> records) {

		TransferInfo transfer = ingestionService.createUpdateRecords(skipdupes, records, headers.getUserEmail());
		return createUpdateRecordsResponseMapper.map(transfer, records);
	}

	@GetMapping("/versions/{id}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<RecordVersions> getRecordVersions(
			@PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
					message = ValidationDoc.INVALID_RECORD_ID) String id) {
		return new ResponseEntity<RecordVersions>(this.queryService.listVersions(id), HttpStatus.OK);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> purgeRecord(@PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
			message = ValidationDoc.INVALID_RECORD_ID) String id) {
		this.recordService.purgeRecord(id);
		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}

	@PostMapping("/{id}:delete")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> deleteRecord(@PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
			message = ValidationDoc.INVALID_RECORD_ID) String id) {
		this.recordService.deleteRecord(id, this.headers.getUserEmail());
		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}

	@GetMapping("/{id}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<String> getLatestRecordVersion(
			@PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
					message = ValidationDoc.INVALID_RECORD_ID) String id,
			@RequestParam(name = "attribute", required = false) String[] attributes) {
		return new ResponseEntity<String>(this.queryService.getRecordInfo(id, attributes), HttpStatus.OK);
	}

	@GetMapping("/{id}/{version}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<String> getSpecificRecordVersion(
			@PathVariable("id") @Pattern(regexp = ValidationDoc.RECORD_ID_REGEX,
					message = ValidationDoc.INVALID_RECORD_ID) String id,
			@PathVariable("version") long version,
			@RequestParam(name = "attribute", required = false) String[] attributes) {
		return new ResponseEntity<String>(this.queryService.getRecordInfo(id, version, attributes), HttpStatus.OK);
	}
}