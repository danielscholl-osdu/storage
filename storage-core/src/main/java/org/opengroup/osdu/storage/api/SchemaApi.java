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
import javax.validation.constraints.NotNull;

import org.opengroup.osdu.core.common.model.storage.validation.ValidKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.service.SchemaService;
import org.springframework.web.context.annotation.RequestScope;

@RestController
@RequestMapping("schemas")
@RequestScope
@Validated
public class SchemaApi {
	private SchemaService schemaService;

	// @InjectMocks and @Autowired together only work on setter DI
	@Autowired
	public void setSchemaService(SchemaService schemaService)
	{
		this.schemaService = schemaService;
	}

	// This endpoint is deprecated as of M6, replaced by schema service.  In M7 this endpoint will be deleted
	@Deprecated
	@PostMapping
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> createSchema(@Valid @NotNull @RequestBody Schema schema)
	{
		this.schemaService.createSchema(schema);
		return new ResponseEntity<Void>(HttpStatus.CREATED);
	}

	// This endpoint is deprecated as of M6, replaced by schema service.  In M7 this endpoint will be deleted
	@Deprecated
	@GetMapping("/{kind}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.VIEWER + "', '" + StorageRole.CREATOR + "', '" + StorageRole.ADMIN + "')")
	public ResponseEntity<Schema> getSchema(@PathVariable("kind") @ValidKind String kind) {
		return new ResponseEntity<Schema>(this.schemaService.getSchema(kind), HttpStatus.OK);
	}

	// This endpoint is deprecated as of M6. In M7 this endpoint will be deleted
	@Deprecated
	@DeleteMapping("/{kind}")
	@PreAuthorize("@authorizationFilter.hasRole('" + StorageRole.ADMIN + "')")
	public ResponseEntity<Void> deleteSchema(@PathVariable("kind") @ValidKind String kind) {
		this.schemaService.deleteSchema(kind);
		return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
	}
}