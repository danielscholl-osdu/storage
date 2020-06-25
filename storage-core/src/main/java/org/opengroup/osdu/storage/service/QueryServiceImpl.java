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

package org.opengroup.osdu.storage.service;

import static java.util.Collections.singletonList;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.storage.PersistenceHelper;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.core.common.model.http.AppException;

@Service
public class QueryServiceImpl implements QueryService {

	@Autowired
	private IRecordsMetadataRepository recordRepository;

	@Autowired
	private ICloudStorage cloudStorage;

	@Autowired
	private TenantInfo tenant;

	@Autowired
	private StorageAuditLogger auditLogger;

	@Autowired
	private JaxRsDpsLog logger;

	@Autowired
	private EntitlementsAndCacheServiceImpl entitlementsAndCacheService;

	@Autowired
	private DpsHeaders dpsHeaders;

	@Override
	public String getRecordInfo(String id, String[] attributes) {
		try {
			String specificVersion = this.getRecord(id, null, attributes);
			this.auditLogger.readLatestVersionOfRecordSuccess(singletonList(id));
			return specificVersion;
		} catch (AppException e) {
			this.auditLogger.readLatestVersionOfRecordFail(singletonList(id));
			throw e;
		}
	}

	@Override
	public String getRecordInfo(String id, long version, String[] attributes) {
		try {
			String specificVersion = this.getRecord(id, version, attributes);
			this.auditLogger.readSpecificVersionOfRecordSuccess(singletonList(id));
			return specificVersion;
		} catch (AppException e) {
			this.auditLogger.readSpecificVersionOfRecordFail(singletonList(id));
			throw e;
		}
	}

	@Override
	public RecordVersions listVersions(String recordId) {
		// all the version numbers
		RecordMetadata recordMetadata = this.getRecordFromRepository(recordId);

		if (!this.cloudStorage.hasAccess(recordMetadata)) {
			this.auditLogger.readAllVersionsOfRecordFail(singletonList(recordId));
			throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
					"The user is not authorized to perform this action");
		}

		List<Long> versions = new ArrayList<>();
		recordMetadata.getGcsVersionPaths().forEach(version -> {
			String[] tokens = version.split("/");
			versions.add(Long.parseLong(tokens[tokens.length - 1]));
		});

		this.auditLogger.readAllVersionsOfRecordSuccess(singletonList(recordId));

		RecordVersions recordVersions = new RecordVersions();
		recordVersions.setRecordId(recordId);
		recordVersions.setVersions(versions);

		return recordVersions;
	}

	private String getRecord(String recordId, Long version, String[] attributes) {

		RecordMetadata recordMetadata = this.getRecordFromRepository(recordId);

		if (!recordMetadata.hasVersion()) {
			this.logger.warning(String.format("Record %s does not have versions available", recordMetadata.getId()));
			throw new AppException(HttpStatus.SC_NOT_FOUND, "Record Not Found", "No version available for this record.");
		}

		Long actualVersion = version == null ? recordMetadata.getLatestVersion() : version;

		return this.fetchRecord(recordMetadata, actualVersion, attributes);
	}

	private RecordMetadata getRecordFromRepository(String recordId) {

		String tenantName = tenant.getName();
		if (!Record.isRecordIdValid(recordId, tenantName)) {
			String msg = String
					.format("The record '%s' does not belong to account '%s'", recordId, tenantName)
					.replace('\n', '_').replace('\r', '_');

			throw new AppException(HttpStatus.SC_BAD_REQUEST, "Invalid record ID", msg);
		}

		RecordMetadata recordMetadata = this.recordRepository.get(recordId);

		if (recordMetadata == null) {
			throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found",
					String.format("The record '%s' was not found", recordId));
		}

		return recordMetadata;
	}

	private String fetchRecord(RecordMetadata recordMetadata, Long version, String[] attributes) {

		RecordState recordStatus = recordMetadata.getStatus();

		// Verify if the record status is active
		if (!recordStatus.equals(RecordState.active)) {
			throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found",
					"The record with the given ID is not active");
		}

		String blob = this.cloudStorage.read(recordMetadata, version, true);
		// post acl check, enforce application data restriction
		List<RecordMetadata> recordMetadataList = new ArrayList<>();
		recordMetadataList.add(recordMetadata);
		List<RecordMetadata> postAclCheck = this.entitlementsAndCacheService.hasValidAccess(recordMetadataList, this.dpsHeaders);

		if (postAclCheck == null || postAclCheck.isEmpty()) {
			throw new AppException(HttpStatus.SC_FORBIDDEN, "Access denied",
					"The user does not have access to the record");
		}

		// TODO REMOVE AFTER MIGRATION
		if (Strings.isNullOrEmpty(blob)) {
			throw new AppException(HttpStatus.SC_NOT_FOUND, "Record version not found",
					"The requested record version was not found");
		}

		List<String> validAttributes = PersistenceHelper.getValidRecordAttributes(attributes);

		JsonElement jsonRecord = new JsonParser().parse(blob);

		// Filter out data sub properties
		if (!validAttributes.isEmpty()) {
			jsonRecord = PersistenceHelper.filterRecordDataFields(jsonRecord, validAttributes);
		}

		return PersistenceHelper.combineRecordMetaDataAndRecordData(jsonRecord, recordMetadata, version);

	}
}
