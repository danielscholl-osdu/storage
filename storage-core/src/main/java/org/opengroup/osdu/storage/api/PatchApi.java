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

import javax.validation.Valid;

import org.opengroup.osdu.storage.service.BulkUpdateRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.RequestScope;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;

@RestController
@RequestMapping("records")
@RequestScope
@Validated
public class PatchApi {

	@Autowired
	private DpsHeaders headers;

	@Autowired
	private BulkUpdateRecordService bulkUpdateRecordService;

	@PatchMapping()
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<BulkUpdateRecordsResponse> updateRecordsMetadata(@RequestBody @Valid RecordBulkUpdateParam recordBulkUpdateParam) {
		BulkUpdateRecordsResponse response = this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.headers.getUserEmail());
		if (!response.getLockedRecordIds().isEmpty() || !response.getNotFoundRecordIds().isEmpty() || !response.getUnAuthorizedRecordIds().isEmpty()) {
			return new ResponseEntity<>(response, HttpStatus.PARTIAL_CONTENT);
		} else {
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
	}
}
