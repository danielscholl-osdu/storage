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

package org.opengroup.osdu.storage.logging;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.List;
import java.util.function.Consumer;

@Component
@RequestScope
public class StorageAuditLogger {

	@Autowired
	private JaxRsDpsLog logger;

	@Autowired
	private DpsHeaders dpsHeaders;

	private StorageAuditEvents events = null;

	@Autowired
	private Consumer<AuditPayload> readAuditLogsConsumer;

	private StorageAuditEvents getAuditEvents() {
		if (this.events == null) {
			this.events = new StorageAuditEvents(this.dpsHeaders.getUserEmail());
		}
		return this.events;
	}

	public void createOrUpdateRecordsSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getCreateOrUpdateRecordsEventSuccess(resource));
	}

	public void createOrUpdateRecordsFail(List<String> resource) {
		this.writeLog(this.getAuditEvents().getCreateOrUpdateRecordsEventFail(resource));
	}

	public void deleteRecordSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getDeleteRecordEventSuccess(resource));
	}

	public void deleteRecordFail(List<String> resource) {
		this.writeLog(this.getAuditEvents().getDeleteRecordEventFail(resource));
	}

	public void purgeRecordSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getPurgeRecordEventSuccess(resource));
	}

	public void purgeRecordFail(List<String> resource) {
		this.writeLog(this.getAuditEvents().getPurgeRecordEventFail(resource));
	}

	public void purgeRecordVersionsSuccess(String recordId, List<String> resource) {
		this.writeLog(this.getAuditEvents().getPurgeRecordVersionsEventSuccess(recordId, resource));
	}

	public void purgeRecordVersionsFail(String recordId, List<String> resource) {
		this.writeLog(this.getAuditEvents().getPurgeRecordVersionsEventFail(recordId, resource));
	}

	public void readAllVersionsOfRecordSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadAllVersionsOfRecordSuccess(resource));
	}

	public void readAllVersionsOfRecordFail(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadAllVersionsOfRecordFail(resource));
	}

	public void readSpecificVersionOfRecordSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadSpecificVersionOfRecordSuccess(resource));
	}

	public void readSpecificVersionOfRecordFail(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadSpecificVersionOfRecordFail(resource));
	}

	public void readLatestVersionOfRecordSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadLatestVersionOfRecordSuccess(resource));
	}

	public void readLatestVersionOfRecordFail(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadLatestVersionOfRecordFail(resource));
	}

	public void readMultipleRecordsSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadMultipleRecordsSuccess(resource));
	}

	public void readAllRecordsOfGivenKindSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadAllRecordsOfGivenKindSuccess(resource));
	}

	public void readAllKindsSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getAllKindsEventSuccess(resource));
	}

	public void createSchemaSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getCreateSchemaEventSuccess(resource));
	}

	public void deleteSchemaSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getDeleteSchemaEventSuccess(resource));
	}

	public void readSchemaSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(this.getAuditEvents().getReadSchemaEventSuccess(resource));
	}

	public void updateRecordsComplianceStateSuccess(List<String> resource) {
		this.writeLog(this.getAuditEvents().getUpdateRecordsComplianceStateEventSuccess(resource));
	}

	public void readMultipleRecordsWithOptionalConversionSuccess(List<String> resource) {
		readAuditLogsConsumer.accept(getAuditEvents().getReadMultipleRecordsWithOptionalConversionSuccess(resource));
	}

	public void readMultipleRecordsWithOptionalConversionFail(List<String> resource) {
		readAuditLogsConsumer.accept(getAuditEvents().getReadMultipleRecordsWithOptionalConversionFail(resource));
	}

	private void writeLog(AuditPayload log) {
		this.logger.audit(log);
	}
}
