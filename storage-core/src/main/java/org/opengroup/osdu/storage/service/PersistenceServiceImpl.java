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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.storage.IPersistenceService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PersistenceServiceImpl implements IPersistenceService {

	@Autowired
	private IRecordsMetadataRepository recordRepository;

	@Autowired
	private ICloudStorage cloudStorage;

	@Autowired
	private IMessageBus pubSubClient;

	@Autowired
	private DpsHeaders headers;

	@Autowired
	private JaxRsDpsLog logger;

	@Override
	public void persistRecordBatch(TransferBatch transfer) {

		List<RecordProcessing> recordsProcessing = transfer.getRecords();
		List<RecordMetadata> recordsMetadata = new ArrayList<>(recordsProcessing.size());

		PubSubInfo[] pubsubInfo = new PubSubInfo[recordsProcessing.size()];

		for (int i = 0; i < recordsProcessing.size(); i++) {
			RecordProcessing processing = recordsProcessing.get(i);
			RecordMetadata recordMetadata = processing.getRecordMetadata();
			recordsMetadata.add(recordMetadata);
			pubsubInfo[i] = new PubSubInfo(recordMetadata.getId(), recordMetadata.getKind(), OperationType.create);
		}

		this.commitBatch(recordsProcessing, recordsMetadata);
		this.pubSubClient.publishMessage(this.headers, pubsubInfo);
	}

    private void commitBatch(List<RecordProcessing> recordsProcessing, List<RecordMetadata> recordsMetadata) {

		try {
			this.commitCloudStorageTransaction(recordsProcessing);
			this.commitDatastoreTransaction(recordsMetadata);
		} catch (AppException e) {
			try {
				this.tryCleanupCloudStorage(recordsProcessing);
			} catch (AppException innerException) {
				e.addSuppressed(innerException);
			}

			throw e;
		}
	}

	@Override
	public List<String> updateMetadata(List<RecordMetadata> recordMetadata, List<String> recordsId, Map<String, String> recordsIdMap) {
		Map<String, Acl> originalAcls = new HashMap<>();
		List<String> lockedRecords = new ArrayList<>();
		List<RecordMetadata> validMetadata = new ArrayList<>();
		try {
			originalAcls = this.cloudStorage.updateObjectMetadata(recordMetadata, recordsId, validMetadata, lockedRecords, recordsIdMap);
			this.commitDatastoreTransaction(validMetadata);
		} catch (NotImplementedException e) {
			throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
		} catch (Exception e) {
			this.logger.warning("Reverting meta data changes");
			try {
				this.cloudStorage.revertObjectMetadata(recordMetadata, originalAcls);
			} catch (NotImplementedException innerEx) {
				throw new AppException(HttpStatus.SC_NOT_IMPLEMENTED, "Not Implemented", "Interface not fully implemented yet");
			} catch (Exception innerEx) {
				e.addSuppressed(innerEx);
			}
			throw e;
		}
		PubSubInfo[] pubsubInfo = new PubSubInfo[recordMetadata.size()];
		for (int i = 0; i < recordMetadata.size(); i++) {
			RecordMetadata metadata = recordMetadata.get(i);
			pubsubInfo[i] = new PubSubInfo(metadata.getId(), metadata.getKind(), OperationType.create);
		}
		this.pubSubClient.publishMessage(this.headers, pubsubInfo);
		return lockedRecords;
	}

	private void tryCleanupCloudStorage(List<RecordProcessing> recordsProcessing) {
		recordsProcessing.forEach(r -> this.cloudStorage.deleteVersion(r.getRecordMetadata(), r.getRecordMetadata().getLatestVersion()));
	}

	private void commitCloudStorageTransaction(List<RecordProcessing> recordsProcessing) {
		this.cloudStorage.write(recordsProcessing.toArray(new RecordProcessing[recordsProcessing.size()]));
	}

	private void commitDatastoreTransaction(List<RecordMetadata> recordsMetadata) {
		try {
			this.recordRepository.createOrUpdate(recordsMetadata);
		} catch (Exception e) {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error writing record.",
					"The server could not process your request at the moment.", e);
		}
	}
}