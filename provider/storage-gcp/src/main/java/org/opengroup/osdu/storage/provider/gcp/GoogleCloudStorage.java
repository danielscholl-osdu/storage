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

package org.opengroup.osdu.storage.provider.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.*;
import com.google.cloud.storage.Acl.Group;
import com.google.cloud.storage.Acl.Role;
import com.google.cloud.storage.Acl.User;
import com.google.gson.Gson;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.provider.gcp.credentials.IStorageFactory;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.opengroup.osdu.core.common.model.http.AppException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

@Repository
public class GoogleCloudStorage implements ICloudStorage {

	private static final String RECORD_WRITING_ERROR_REASON = "Error on writing record";

	@Value("${PUBSUB_SEARCH_TOPIC}")
	public String PUBSUB_SEARCH_TOPIC;

	@Value("${GOOGLE_AUDIENCES}")
	public String GOOGLE_AUDIENCES;

	@Value("${STORAGE_HOSTNAME}")
	public String STORAGE_HOSTNAME;

	@Autowired
	private DpsHeaders headers;

	@Autowired
	private TenantInfo tenant;

	@Autowired
	private IStorageFactory storageFactory;

	@Autowired
	private ExecutorService threadPool;

	@Autowired
	private JaxRsDpsLog log;

	@Override
	public void write(RecordProcessing... records) {
		String bucket = this.getBucketName(this.tenant);

		ObjectMapper mapper = new ObjectMapper();

		List<Callable<Boolean>> tasks = new ArrayList<>();

		for (RecordProcessing record : records) {
			//have to pass these values separately because of multithreading scenario. 'dpsHeaders' and 'tenant' objects are request scoped (autowired) and
			//children threads lose their context down the stream, we get a bean creation exception
			String email = this.headers.getUserEmail();
			String serviceAccount = this.tenant.getServiceAccount();
			String projectId = this.tenant.getProjectId();
			String tenantName = this.tenant.getName();
			tasks.add(() -> this.writeBlobThread(email, serviceAccount, projectId, tenantName, record, mapper, bucket));
		}

		try {
			List<Future<Boolean>> results = this.threadPool.invokeAll(tasks);

			for (Future<Boolean> future : results) {
				future.get();
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			if (e.getCause() instanceof AppException) {
				throw (AppException) e.getCause();
			} else {
				throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error during record ingestion",
						"An unexpected error on writing the record has occurred", e);
			}
		}
	}

	@Override
	public boolean hasAccess(RecordMetadata... records) {

		if (ArrayUtils.isEmpty(records)) {
			return true;
		}

		String bucket = this.getBucketName(this.tenant);
		for (RecordMetadata record : records) {
			if (!record.getStatus().equals(RecordState.active)) {
				continue;
			}

			if (!record.hasVersion()) {
				this.log.warning(String.format("Record %s does not have versions available", record.getId()));
				continue;
			}

			try {
				String path = record.getVersionPath(record.getLatestVersion());
				Blob blob = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName()).get(bucket, path);
				if (blob == null) {
					throw new StorageException(HttpStatus.SC_NOT_FOUND, String.format("'%s' not found", path));
				}
			} catch (StorageException e) {
				// Before we return false, we have to check whether there is any data
				// inconsistency then cleanup and check the access again
				// This makes all the APIs robust to inconsistent data, but will add some
				// latency
				if (!this.hasAccessRobustToDataCorruption(bucket, record, this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName()))) {
					return false;
				}
			}
		}

		return true;
	}

	public boolean hasAccessRobustToDataCorruption(String bucket, RecordMetadata record,
												   Storage storageClientUserCredential) {
		// Get the latest version from GCS by using datafier service account first,
		// since gcs API can't distinguish 404 or 403
		// If datafier can get meaning user does not have permission, if datafier can't
		// get meaning data has been corrupted
		// (This is ok for DR, because DR service only revoke the data store written
		// permission from datafier)
		Storage storageClientDatafierCredential = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName());
		if (storageClientDatafierCredential
				.get(BlobId.of(bucket, record.getVersionPath(record.getLatestVersion()))) != null) {
			return false;
		} else {
			this.log.warning(String.format(
					"Record %s's version metadata (%s) is inconsistent with gcs records, it tries to cleanup",
					record.getId(), String.join(",", record.getGcsVersionPaths())));
			this.log.info(String.format("Cleanup finished for record %s's metadata", record.getId()));
			// No files stored in the bucket, then we always allow to access
			return true;
		}
	}

	@Override
	public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {

		try {
			String path = record.getVersionPath(version);

			byte[] blob = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName()).readAllBytes(this.getBucketName(this.tenant), path);
			return new String(blob, UTF_8);

		} catch (StorageException e) {
			if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
				//exception should be generic, logging can be informative
				throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
			} else if (e.getCode() == HttpStatus.SC_NOT_FOUND) {
				String msg = String.format("Record with id '%s' does not exist", record.getId());
				throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
			} else {
				throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error during record retrieval",
						"An unexpected error on retrieving the record has occurred", e);
			}
		}
	}

	@Override
	public Map<String, String> read(Map<String, String> objects) {

		String bucketName = this.getBucketName(this.tenant);

		Map<String, String> map = new HashMap<>();

		List<Callable<Boolean>> tasks = new ArrayList<>();

		for (Map.Entry<String, String> object : objects.entrySet()) {
			//have to pass these values separately because of multithreading scenario. 'dpsHeaders' and 'tenant' objects are request scoped (autowired) and
			//children threads lose their context down the stream, we get a bean creation exception
			String email = this.headers.getUserEmail();
			String serviceAccount = this.tenant.getServiceAccount();
			String projectId = this.tenant.getProjectId();
			String tenantName = this.tenant.getName();
			tasks.add(() -> this.readBlobThread(email, serviceAccount, projectId, tenantName, object.getValue(), bucketName, map, object.getKey()));
		}

		try {
			this.threadPool.invokeAll(tasks);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return map;
	}

	@Override
	public Map<String, String> getHash(Collection<RecordMetadata> records) {

		String bucket = this.getBucketName(this.tenant);

		BlobId[] blobIds = records
				.stream()
				.map(rm -> BlobId.of(bucket, rm.getVersionPath(rm.getLatestVersion())))
				.toArray(BlobId[]::new);

		List<Blob> blobs = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName()).get(blobIds);

		Map<String, String> hashes = new HashMap<>();

		int i = 0;
		for (RecordMetadata rm : records) {
			Blob blob = blobs.get(i);
			String hash = blob == null ? "" : blob.getCrc32c();
			hashes.put(rm.getId(), hash);
			i++;
		}

		return hashes;
	}

	@Override
	public void delete(RecordMetadata record) {
		if (!record.hasVersion()) {
			this.log.warning(String.format("Record %s does not have versions available", record.getId()));
			return;
		}

		boolean mustSubmit = false;
		String bucket = this.getBucketName(this.tenant);
		Storage storage = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName());

		StorageBatch batch = storage.batch();

		try {
			Blob blob = storage.get(BlobId.of(bucket, record.getVersionPath(record.getLatestVersion())));

			if (blob == null) {
				String msg = String.format("Record with id '%s' does not exist", record.getId());
				throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
			}

			String[] versionFiles = record.getGcsVersionPaths().toArray(new String[record.getGcsVersionPaths().size()]);

			for (String path : versionFiles) {
				batch.delete(bucket, path);
				mustSubmit = true;
			}

		} catch (StorageException e) {
			throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
		}

		if (mustSubmit) {
			batch.submit();
		}
	}

	@Override
	public void deleteVersion(RecordMetadata record, Long version) {

		boolean mustSubmit = false;
		String bucket = this.getBucketName(this.tenant);
		Storage storage = this.storageFactory.getStorage(this.headers.getUserEmail(), this.tenant.getServiceAccount(), this.tenant.getProjectId(), this.tenant.getName());

		StorageBatch batch = storage.batch();

		try {
			if (!record.hasVersion()) {
				this.log.warning(String.format("Record %s does not have versions available", record.getId()));
			}

			Blob blob = storage.get(BlobId.of(bucket, record.getVersionPath(version)));

			if (blob == null) {
				this.log.warning(String.format("Record with id '%s' does not exist, unable to purge version: %s", record.getId(), version));
			}
			batch.delete(bucket, record.getVersionPath(version));
			mustSubmit = true;

		} catch (StorageException e) {
			throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
		}

		if (mustSubmit) {
			batch.submit();
		}
	}

	@Override
	public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
		Gson gson = new Gson();
		Crc32c checksumGenerator = new Crc32c();
		RecordMetadata updatedRecordMetadata = kv.getKey();
		RecordData recordData = kv.getValue();
		String recordHash = hashMap.get(updatedRecordMetadata.getId());

		String newRecordStr = gson.toJson(recordData);
		byte[] bytes = newRecordStr.getBytes(StandardCharsets.UTF_8);
		checksumGenerator.update(bytes, 0, bytes.length);
		bytes = checksumGenerator.getValueAsBytes();
		String newHash = new String(encodeBase64(bytes));

		if (newHash.equals(recordHash)) {
			transfer.getSkippedRecords().add(updatedRecordMetadata.getId());
			return true;
		}else{
			return false;
		}
	}

	private boolean writeBlobThread(String userId, String serviceAccount, String projectId, String tenantName, RecordProcessing processing, ObjectMapper mapper, String bucket) {

		RecordMetadata metadata = processing.getRecordMetadata();

		org.opengroup.osdu.core.common.model.entitlements.Acl storageAcl = metadata.getAcl();
		String objectPath = metadata.getVersionPath(metadata.getLatestVersion());

		List<Acl> acls = new ArrayList<>();

		for (String acl : storageAcl.getViewers()) {
			acls.add(Acl.newBuilder(new Group(acl), Role.READER).build());
		}

		for (String acl : storageAcl.getOwners()) {
			acls.add(Acl.newBuilder(new Group(acl), Role.OWNER).build());
		}

		acls.add(Acl.newBuilder(new User(serviceAccount), Role.OWNER).build());

		BlobInfo blobInfo = BlobInfo
				.newBuilder(bucket, objectPath)
				.setContentType(MediaType.APPLICATION_JSON_VALUE)
				.setContentEncoding(UTF_8.toString())
				.setAcl(acls)
				.build();

		try {
			String content = mapper.writeValueAsString(processing.getRecordData());
			this.storageFactory.getStorage(userId, serviceAccount, projectId, tenantName).create(blobInfo, content.getBytes(UTF_8));
		} catch (StorageException e) {
			if (e.getCode() == HttpStatus.SC_BAD_REQUEST) {
				throw new AppException(HttpStatus.SC_BAD_REQUEST, RECORD_WRITING_ERROR_REASON, e.getMessage(), e);
			}

			if (e.getCode() == HttpStatus.SC_FORBIDDEN) {
				throw new AppException(HttpStatus.SC_FORBIDDEN, RECORD_WRITING_ERROR_REASON,
						"User does not have permission to write the records.", e);
			}

			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_WRITING_ERROR_REASON,
					"An unexpected error on writing the record has occurred", e);
		} catch (JsonProcessingException e) {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, RECORD_WRITING_ERROR_REASON,
					"An unexpected error on writing the record has occurred", e);
		}

		return true;
	}

	private boolean readBlobThread(String userId, String serviceAccount, String projectId, String tenantName, String object, String bucket, Map<String, String> map, String recordId) {
		String[] tokens = object.split("/");
		String key = tokens[tokens.length - 2];

		try {
			String value = new String(this.storageFactory.getStorage(userId, serviceAccount, projectId, tenantName).readAllBytes(bucket, object), UTF_8);
			map.put(key, value);
		} catch (StorageException e) {
			map.put(key, null);
		}

		return true;
	}

	private static String getBucketName(TenantInfo tenant) {
		return String.format("%s-records", tenant.getProjectId());
	}
}