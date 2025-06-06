// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.provider.azure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.azure.blobstorage.BlobStore;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;
import org.opengroup.osdu.storage.provider.azure.repository.GroupsInfoRepository;
import org.opengroup.osdu.storage.provider.azure.repository.RecordMetadataRepository;
import org.opengroup.osdu.storage.provider.azure.util.EntitlementsHelper;
import org.opengroup.osdu.storage.provider.azure.util.RecordUtil;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.util.CrcHashGenerator;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.inject.Named;
import java.util.*;
import java.util.concurrent.*;

@Repository
public class CloudStorageImpl implements ICloudStorage {
    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private ExecutorService threadPool;

    @Autowired
    private RecordMetadataRepository recordRepository;

    @Autowired
    private BlobStore blobStore;

    @Autowired
    private GroupsInfoRepository groupsInfoRepository;

    @Autowired
    private RecordUtil recordUtil;

    @Autowired
    private CrcHashGenerator crcHashGenerator;

    @Autowired
    private EntitlementsHelper entitlementsHelper;

    @Autowired
    @Named("STORAGE_CONTAINER_NAME")
    private String containerName;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void write(RecordProcessing... recordsProcessing) {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        String dataPartitionId = headers.getPartitionId();
        for (RecordProcessing rp : recordsProcessing) {
            tasks.add(() -> this.writeBlobThread(rp, dataPartitionId));
        }

        try {
            for (Future<Boolean> result : this.threadPool.invokeAll(tasks)) {
                result.get();
            }
            MDC.put("record-count",String.valueOf(tasks.size()));
        } catch (InterruptedException | ExecutionException e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error during record ingestion",
                    "An unexpected error on writing the record has occurred", e);
        }
    }

    @Override
    public Map<String, Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap, Optional<CollaborationContext> collaborationContext) {

        Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls = new HashMap<>();
        Map<String, RecordMetadata> currentRecords = this.recordRepository.get(recordsId, collaborationContext);

        for (RecordMetadata recordMetadata : recordsMetadata) {
            String id = recordMetadata.getId();
            String idWithVersion = recordsIdMap.get(id);
            // validate that updated metadata has the same version
            if (!id.equalsIgnoreCase(idWithVersion)) {
                long previousVersion = Long.parseLong(idWithVersion.split(":")[3]);
                long currentVersion = currentRecords.get(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext)).getLatestVersion();
                // if version is different, do not update
                if (previousVersion != currentVersion) {
                    lockedRecords.add(idWithVersion);
                    continue;
                }
            }
            validMetadata.add(recordMetadata);
            originalAcls.put(recordMetadata.getId(), currentRecords.get(CollaborationContextUtil.composeIdWithNamespace(id, collaborationContext)).getAcl());
        }
        return originalAcls;
    }

    @Override
    public void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls, Optional<CollaborationContext> collaborationContext) {
        List<RecordMetadata> originalAclRecords = new ArrayList<>();
        for (RecordMetadata recordMetadata : recordsMetadata) {
            Acl acl = originalAcls.get(recordMetadata.getId());
            recordMetadata.setAcl(acl);
            originalAclRecords.add(recordMetadata);
        }
        try {
            this.recordRepository.createOrUpdate(originalAclRecords, collaborationContext);
        } catch (Exception e) {
            throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error while reverting metadata: in revertObjectMetadata.","Internal server error.", e);
        }
    }

    private boolean writeBlobThread(RecordProcessing rp, String dataPartitionId)
    {
        Gson gson = new GsonBuilder().serializeNulls().create();
        RecordMetadata rmd = rp.getRecordMetadata();
        String path = buildPath(rmd);
        String content = gson.toJson(rp.getRecordData());
        blobStore.writeToStorageContainer(dataPartitionId, path, content, containerName);
        return true;
    }

    @Override
    public Map<String, String> getHash(Collection<RecordMetadata> records) {
        Map<String, String> hashes = new HashMap<>();
        RecordData data;
        for (RecordMetadata rm : records) {
            String jsonData = this.read(rm, rm.getLatestVersion(), false);
            try {
                data = objectMapper.readValue(jsonData, RecordData.class);
            } catch (JsonProcessingException e){
                logger.error(String.format("Error while converting metadata for record %s", rm.getId()), e);
                continue;
            }
            String hash = crcHashGenerator.getHash(data);
            hashes.put(rm.getId(), hash);
        }
        return hashes;
    }

    @Override
    public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
        RecordMetadata updatedRecordMetadata = kv.getKey();
        RecordData recordData = kv.getValue();
        String recordHash = hashMap.get(updatedRecordMetadata.getId());

        String newHash = crcHashGenerator.getHash(recordData);

        if (newHash.equals(recordHash)) {
            transfer.getSkippedRecords().add(updatedRecordMetadata.getId());
            return true;
        }else{
            return false;
        }
    }

    @Override
    public void delete(RecordMetadata record) {
        if (!record.hasVersion()) {
            this.logger.warning(String.format("Record %s does not have versions available", record.getId()));
            return;
        }

        validateOwnerAccessToRecord(record);
        for (String path : record.getGcsVersionPaths()) {
            if(recordRepository.getMetadataDocumentCountForBlob(path) > 0) {
                this.logger.warning(String.format("More than 1 metadata documents reference the StorageBlob, skip purge", path));
                return;
            }
            blobStore.deleteFromStorageContainer(headers.getPartitionId(), path, containerName);
        }
    }

    @Override
    public void deleteVersion(RecordMetadata record, Long version) {
        validateOwnerAccessToRecord(record);
        String path = this.buildPath(record, version.toString());
        blobStore.deleteFromStorageContainer(headers.getPartitionId(), path, containerName);
    }

    @Override
    public void deleteVersions(List<String> versionPaths) {
        versionPaths.stream().forEach(versionPath -> {
            try {
                blobStore.deleteFromStorageContainer(headers.getPartitionId(), versionPath, containerName);
            } catch (AppException ex) {
                //  It is possible that the record may have a version instance that is present in the metadata store and absent from the the blob store.
                // This is a known inconsistency caused when we fail to successfully add the version instance to the blob store.
                // To handle it we should ignore deletions from the blob store that result in a 404 (not found) error.
                if (ex.getError().getCode() == 404) {
                    this.logger.warning(String.format("Deletion Failed. Tried to delete non-existent version in storage account: %s", versionPath));
                }
                else {
                    throw ex;
                }
            }
        });
    }

    @Override
    public boolean hasAccess(RecordMetadata... records) {
        if (ArrayUtils.isEmpty(records)) {
            return true;
        }

        boolean hasAtLeastOneActiveRecord = false;
        for (RecordMetadata record : records) {
            if (!record.getStatus().equals(RecordState.active)) {
                continue;
            }

            if (!record.hasVersion()) {
                this.logger.warning(String.format("Record %s does not have versions available", record.getId()));
                continue;
            }

            hasAtLeastOneActiveRecord = true;
            if (entitlementsHelper.hasViewerAccessToRecord(record))
                return true;
        }

        return !hasAtLeastOneActiveRecord;
    }


    private void validateOwnerAccessToRecord(RecordMetadata record)
    {
        if (!entitlementsHelper.hasOwnerAccessToRecord(record)) {
            logger.warning(String.format("%s has no owner access to %s", headers.getUserEmail(), record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG);
        }
    }

    private void validateReadAccessToRecord(RecordMetadata record) {
        if (!entitlementsHelper.hasViewerAccessToRecord(record) && !entitlementsHelper.hasOwnerAccessToRecord(record)) {
            logger.warning(String.format("%s has no owner/viewer access to %s", headers.getUserEmail(), record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN,  ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG);
        }
    }

    @Override
    public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
        validateReadAccessToRecord(record);
        String path = this.buildPath(record, version.toString());
        try {
            return blobStore.readFromStorageContainer(headers.getPartitionId(), path, containerName);
        } catch (AppException ex) {
            if (ex.getError().getCode() == HttpStatus.SC_NOT_FOUND) {
                //we've encountered data inconsistency. Record is present in cosmosDb but not found in blob storage
                //we'll attempt to recover the data object
                try {
                    restoreSpecifiedBlob(headers.getPartitionId(), path, containerName);
                    return blobStore.readFromStorageContainer(headers.getPartitionId(), path, containerName);
                } catch (AppException e) {
                    throw e;
                }
            } else {
                throw ex;
            }
        }
    }

    @Override
    public Map<String, String> read(Map<String, String> objects, Optional<CollaborationContext> collaborationContext) {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        Map<String, String> map = new ConcurrentHashMap<>();

        List<String> recordIds = new ArrayList<>(objects.keySet());
        Map<String, RecordMetadata> recordsMetadata = this.recordRepository.get(recordIds, collaborationContext);

        String dataPartitionId = headers.getPartitionId();

        for (String recordId : recordIds) {
            RecordMetadata recordMetadata = recordsMetadata.get(CollaborationContextUtil.composeIdWithNamespace(recordId, collaborationContext));
            if (!entitlementsHelper.hasViewerAccessToRecord(recordMetadata)) {
                continue;
            }
            String path = objects.get(recordId);
            tasks.add(() -> this.readBlobThread(recordId, path, map, dataPartitionId));
        }

        try {
            for (Future<Boolean> result : this.threadPool.invokeAll(tasks)) {
                result.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            String errorMessage = "Unable to process parallel blob download";
            logger.error(errorMessage, e);
            throw new AppException(500, errorMessage, e.getMessage());
        }

        return map;
    }

    private void restoreSpecifiedBlob(String dataPartitionId, String path, String containerName) {
        blobStore.undeleteFromStorageContainer(dataPartitionId, path, containerName);
    }

    private boolean readBlobThread(String key, String path, Map<String, String> map, String dataPartitionId) {
        try {
            String content = blobStore.readFromStorageContainer(dataPartitionId, path, containerName);
            map.put(key, content);
        } catch (AppException e) {
            if(e.getError().getCode() == HttpStatus.SC_NOT_FOUND) {
                //we've encountered data inconsistency. Record is present in cosmosDb but not found in blob storage
                //we'll attempt to recover the data object
                try {
                    restoreSpecifiedBlob(dataPartitionId, path, containerName);
                    String content = blobStore.readFromStorageContainer(dataPartitionId, path, containerName);
                    map.put(key, content);
                } catch (AppException ex) {
                    logger.error("Unknown error occurred while restoring and then reading the specified blob", ex);
                }
            }
        }
        return true;
    }

    private String buildPath(RecordMetadata record)
    {
        String path = record.getKind() + "/" + record.getId() + "/" + record.getLatestVersion();
        return path;
    }

    private String buildPath(RecordMetadata record, String version) {
        String kind = recordUtil.getKindForVersion(record, version);
        String path = kind + "/" + record.getId() + "/" + version;
        return path;
    }
}
