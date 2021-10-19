/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

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

package org.opengroup.osdu.storage.provider.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.gcp.obm.driver.Driver;
import org.opengroup.osdu.core.gcp.obm.driver.DriverRuntimeException;
import org.opengroup.osdu.core.gcp.obm.driver.S3CompatibleErrors;
import org.opengroup.osdu.core.gcp.obm.model.Blob;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.encodeBase64;

@Repository
@ConditionalOnProperty(name = "osmDriver")
@RequiredArgsConstructor
public class ObmGoogleCloudStorage implements ICloudStorage {

    private static final String RECORD_WRITING_ERROR_REASON = "Error on writing record";
    private static final String RECORD_DOES_NOT_HAVE_VERSIONS_AVAILABLE_MSG = "Record %s does not have versions available";
    private static final String ERROR_ON_WRITING_THE_RECORD_HAS_OCCURRED_MSG = "An unexpected error on writing the record has occurred";

    private final Driver storage;

//	@Value("${PUBSUB_SEARCH_TOPIC}")
//	public String pubsubSearchTopic;

//	@Value("${GOOGLE_AUDIENCES}")
//	public String googleAudiences;

//	@Value("${STORAGE_HOSTNAME}")
//	public String storageHostname;


//	@Autowired
//	private StorageConfigProperties properties;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private TenantInfo tenant;

//	@Autowired
//	private IStorageFactory storageFactory;

    @Autowired
    private IRecordsMetadataRepository<?> recordRepository;

    @Autowired
    private IEntitlementsExtensionService entitlementsService;

    @Autowired
    private ExecutorService threadPool;

    @Autowired
    private JaxRsDpsLog log;

//	@Autowired
//	public void setProperties(StorageConfigProperties properties){
//		this.properties = properties;
//	}

    @Override
    public void write(RecordProcessing... records) {
        String bucket = getBucketName(this.tenant);

        ObjectMapper mapper = new ObjectMapper();

        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (RecordProcessing record : records) {
            //have to pass these values separately because of multithreading scenario. 'dpsHeaders' and 'tenant' objects are request scoped (autowired) and
            //children threads lose their context down the stream, we get a bean creation exception
            String email = this.headers.getUserEmail();
            String serviceAccount = this.tenant.getServiceAccount();
            String projectId = this.tenant.getProjectId();
            String tenantName = this.tenant.getName();

            RecordMetadata metadata = record.getRecordMetadata();
            validateMetadata(metadata);

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
                        ERROR_ON_WRITING_THE_RECORD_HAS_OCCURRED_MSG, e);
            }
        }
    }

    @Override
    public Map<String, Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap) {
        String bucket = getBucketName(this.tenant);
        //Storage storage = this.storageFactory.getStorage(this.headers.getUserEmail(), tenant.getServiceAccount(), tenant.getProjectId(), tenant.getName(), properties.isEnableImpersonalization());
        Map<String, Acl> originalAcls = new HashMap<>();
        Map<String, RecordMetadata> currentRecords = this.recordRepository.get(recordsId);

        for (RecordMetadata recordMetadata : recordsMetadata) {
            String id = recordMetadata.getId();
            String idWithVersion = recordsIdMap.get(id);

            if (!id.equalsIgnoreCase(idWithVersion)) {
                long previousVersion = Long.parseLong(idWithVersion.split(":")[3]);
                long currentVersion = currentRecords.get(id).getLatestVersion();
                if (previousVersion != currentVersion) {
                    lockedRecords.add(idWithVersion);
                    continue;
                }
            }
            validMetadata.add(recordMetadata);
            Blob blob = storage.getBlob(bucket, recordMetadata.getVersionPath(recordMetadata.getLatestVersion()));
            originalAcls.put(recordMetadata.getId(), currentRecords.get(id).getAcl());
            //blob.update();
        }

        return originalAcls;
    }

    @Override
    public void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, Acl> originalAcls) {
        String bucket = getBucketName(this.tenant);

        for (RecordMetadata recordMetadata : recordsMetadata) {
            Blob blob = storage.getBlob(bucket, recordMetadata.getVersionPath(recordMetadata.getLatestVersion()));
            //blob.update();
        }
    }

    @Override
    public boolean hasAccess(RecordMetadata... records) {

        if (ArrayUtils.isEmpty(records)) {
            return true;
        }

        String bucket = getBucketName(this.tenant);
        for (RecordMetadata record : records) {
            if (!record.getStatus().equals(RecordState.active)) {
                continue;
            }

            if (!record.hasVersion()) {
                this.log.warning(String.format(RECORD_DOES_NOT_HAVE_VERSIONS_AVAILABLE_MSG, record.getId()));
                continue;
            }

            try {
                String path = record.getVersionPath(record.getLatestVersion());
                Blob blob = storage.getBlob(bucket, path);
                if (blob == null) {
                    throw new DriverRuntimeException(S3CompatibleErrors.NO_SUCH_KEY_CODE, new RuntimeException(String.format("'%s' not found", path)));
                }
            } catch (DriverRuntimeException exception) {
                throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, exception);
            }
        }

        return true;
    }

    @Override
    public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {

        try {
            String path = record.getVersionPath(version);

            byte[] blob = storage.getBlobContent(getBucketName(this.tenant), path);
            return new String(blob, UTF_8);

        } catch (DriverRuntimeException e) {
            if (e.getError().getHttpStatusCode() == HttpStatus.SC_FORBIDDEN) {
                //exception should be generic, logging can be informative
                throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
            } else if (e.getError().getHttpStatusCode() == HttpStatus.SC_NOT_FOUND) {
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

        String bucketName = getBucketName(this.tenant);

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

        String bucket = getBucketName(this.tenant);

        String[] blobIds = records.stream().map(rm -> rm.getVersionPath(rm.getLatestVersion())).toArray(String[]::new);
        Iterable<Blob> blobs = storage.listBlobsByName(bucket, blobIds);

        Map<String, String> hashes = new HashMap<>();

        for (RecordMetadata rm : records) {
            Blob blob = blobs.iterator().next();
            String hash = blob == null ? "" : blob.getEtag();
            hashes.put(rm.getId(), hash);
        }

        return hashes;
    }

    @Override
    public void delete(RecordMetadata record) {
        if (!record.hasVersion()) {
            this.log.warning(String.format(RECORD_DOES_NOT_HAVE_VERSIONS_AVAILABLE_MSG, record.getId()));
            return;
        }

        boolean mustSubmit = false;
        String bucket = getBucketName(this.tenant);
        try {
            Blob blob = storage.getBlob(bucket, record.getVersionPath(record.getLatestVersion()));

            if (blob == null) {
                String msg = String.format("Record with id '%s' does not exist", record.getId());
                throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
            }

            String[] versionFiles = record.getGcsVersionPaths().toArray(new String[record.getGcsVersionPaths().size()]);

            for (String path : versionFiles) {
                storage.deleteBlobs(bucket, versionFiles);
                mustSubmit = true;
            }

        } catch (DriverRuntimeException e) {
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
        }

    }

    @Override
    public void deleteVersion(RecordMetadata record, Long version) {

        boolean mustSubmit = false;
        String bucket = getBucketName(this.tenant);

        try {
            if (!record.hasVersion()) {
                this.log.warning(String.format(RECORD_DOES_NOT_HAVE_VERSIONS_AVAILABLE_MSG, record.getId()));
            }

            Blob blob = storage.getBlob(bucket, record.getVersionPath(version));

            if (blob == null) {
                this.log.warning(String.format("Record with id '%s' does not exist, unable to purge version: %s", record.getId(), version));
            }
            storage.deleteBlob(bucket, record.getVersionPath(version));
            mustSubmit = true;

        } catch (DriverRuntimeException e) {
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG, e);
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
        } else {
            return false;
        }
    }

    private boolean writeBlobThread(String userId, String serviceAccount, String projectId, String tenantName, RecordProcessing processing, ObjectMapper mapper, String bucket) {

        RecordMetadata metadata = processing.getRecordMetadata();
        String objectPath = metadata.getVersionPath(metadata.getLatestVersion());

        Blob blob = Blob.builder().bucket(bucket).name(objectPath).contentType(MediaType.APPLICATION_JSON_VALUE).build();

        try {
            String content = mapper.writeValueAsString(processing.getRecordData());
            storage.createAndGetBlob(blob, content.getBytes(UTF_8));
        } catch (DriverRuntimeException e) {
            if (e.getError().getHttpStatusCode() == HttpStatus.SC_BAD_REQUEST) {
                throw new AppException(HttpStatus.SC_BAD_REQUEST, RECORD_WRITING_ERROR_REASON, e.getMessage(), e);
            }

            if (e.getError().getHttpStatusCode() == HttpStatus.SC_FORBIDDEN) {
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

    private void validateMetadata(RecordMetadata metadata) {
        List<String> aclGroups = new ArrayList<>();

        Collections.addAll(aclGroups, metadata.getAcl().getViewers());
        Collections.addAll(aclGroups, metadata.getAcl().getOwners());

        List<String> groups = entitlementsService.getGroups(headers)
                .getGroups()
                .stream()
                .map(GroupInfo::getEmail)
                .collect(Collectors.toList());

        Optional<String> missingGroup = aclGroups.stream().filter((s) -> !groups.contains(s)).findFirst();

        if (missingGroup.isPresent()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST,
                    RECORD_WRITING_ERROR_REASON,
                    String.format("Could not find group \"%s\".", missingGroup.get()));
        }
    }

    private boolean readBlobThread(String userId, String serviceAccount, String projectId, String tenantName, String object, String bucket, Map<String, String> map, String recordId) {
        String[] tokens = object.split("/");
        String key = tokens[tokens.length - 2];

        try {
            String value = new String(storage.getBlobContent(bucket, object), UTF_8);
            map.put(key, value);
        } catch (DriverRuntimeException e) {
            map.put(key, null);
        }

        return true;
    }

    private static String getBucketName(TenantInfo tenant) {
        return String.format("%s-records", tenant.getProjectId());
    }
}