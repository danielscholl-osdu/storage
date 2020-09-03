/**
 * Copyright 2020 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.ibm;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.ibm.objectstorage.CloudObjectStorageFactory;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.stereotype.Repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;

@Repository
public class CloudObjectStorageImpl implements ICloudStorage {

	@Inject
	private CloudObjectStorageFactory cosFactory;
	
	@Inject
    private EntitlementsAndCacheServiceIBM entitlementsService;

	@Inject
    private IRecordsMetadataRepository recordsMetadataRepository;

	@Inject
    private DpsHeaders headers;

	@Inject
	private JaxRsDpsLog logger;

	AmazonS3 s3Client;

	@PostConstruct
	public void init() {
		s3Client = cosFactory.getClient();
	}

	@Override
	public void write(RecordProcessing... recordsProcessing) {
		
		validateRecordAcls(recordsProcessing);

		Gson gson = new GsonBuilder().serializeNulls().create();

		for (RecordProcessing rp : recordsProcessing) {

			RecordMetadata rmd = rp.getRecordMetadata();
			String itemName = getItemName(rmd);
			String content = gson.toJson(rp.getRecordData());
			byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
			int bytesSize = bytes.length;

			InputStream newStream = new ByteArrayInputStream(bytes);

			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(bytesSize);

			PutObjectRequest req = new PutObjectRequest(getBucketName(), itemName, newStream, metadata);
			s3Client.putObject(req);

			logger.info("Item created!\n" + itemName);

		}

	}

	@Override
	public Map<String, String> getHash(Collection<RecordMetadata> records) {
		Gson gson = new Gson();
		Map<String, String> hashes = new HashMap<>();
		for (RecordMetadata rm : records) {
			String jsonData = this.read(rm, rm.getLatestVersion(), false);
			RecordData data = gson.fromJson(jsonData, RecordData.class);
			String hash = getHash(data);
			hashes.put(rm.getId(), hash);
		}
		return hashes;
	}

	private String getHash(RecordData data) {
		Gson gson = new Gson();
		Crc32c checksumGenerator = new Crc32c();

		String newRecordStr = gson.toJson(data);
		byte[] bytes = newRecordStr.getBytes(StandardCharsets.UTF_8);
		checksumGenerator.update(bytes, 0, bytes.length);
		bytes = checksumGenerator.getValueAsBytes();
		String newHash = new String(encodeBase64(bytes));
		return newHash;
	}

	@Override
	public void delete(RecordMetadata record) {
		validateOwnerAccessToRecord(record);
		String itemName = getItemName(record);
		deleteItem(itemName);
	}

	@Override
	public void deleteVersion(RecordMetadata record, Long version) {
		validateOwnerAccessToRecord(record);
		String itemName = getItemName(record, version);
		deleteItem(itemName);
	}
	
	private void deleteItem(String itemName) {
		logger.info("Delete item: " + itemName);
		try {
			s3Client.deleteObject(getBucketName(), itemName);
			logger.info("Item deleted: " + itemName);
		} catch (Exception  e) {
		    logger.error("Failed to delete item " +itemName);
		}
	}

	@Override
	public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap,
			Map.Entry<RecordMetadata, RecordData> kv) {
		RecordMetadata updatedRecordMetadata = kv.getKey();
		RecordData recordData = kv.getValue();
		String recordHash = hashMap.get(updatedRecordMetadata.getId());

		String newHash = getHash(recordData);

		if (newHash.equals(recordHash)) {
			transfer.getSkippedRecords().add(updatedRecordMetadata.getId());
			return true;
		} else {
			return false;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> updateObjectMetadata(List<RecordMetadata> recordsMetadata, List<String> recordsId, List<RecordMetadata> validMetadata, List<String> lockedRecords, Map<String, String> recordsIdMap) {
		Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls = new HashMap<>();
		Map<String, RecordMetadata> currentRecords = this.recordsMetadataRepository.get(recordsId);
		
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
			originalAcls.put(recordMetadata.getId(), currentRecords.get(id).getAcl());
		}
		return originalAcls;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void revertObjectMetadata(List<RecordMetadata> recordsMetadata, Map<String, org.opengroup.osdu.core.common.model.entitlements.Acl> originalAcls) {
		List<RecordMetadata> originalAclRecords = new ArrayList<>();
		for (RecordMetadata recordMetadata : recordsMetadata) {
			Acl acl = originalAcls.get(recordMetadata.getId());
			recordMetadata.setAcl(acl);
			originalAclRecords.add(recordMetadata);
		}
		try {
			this.recordsMetadataRepository.createOrUpdate(originalAclRecords);
		} catch (Exception e) {
			throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error reverting record.",
					"The server could not process your request at the moment.", e);
		}
	}
	
	@Override
	public boolean hasAccess(RecordMetadata... records) {
		for (RecordMetadata recordMetadata : records) {
            if (!hasViewerAccessToRecord(recordMetadata)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasViewerAccessToRecord(RecordMetadata record)
    {
        boolean isEntitledForViewing = entitlementsService.hasAccessToData(headers,
                new HashSet<>(Arrays.asList(record.getAcl().getViewers())));
        boolean isRecordOwner = record.getUser().equalsIgnoreCase(headers.getUserEmail());
        return isEntitledForViewing || isRecordOwner;
    }

    private boolean hasOwnerAccessToRecord(RecordMetadata record)
    {
        return entitlementsService.hasAccessToData(headers,
                new HashSet<>(Arrays.asList(record.getAcl().getOwners())));
    }

    private void validateOwnerAccessToRecord(RecordMetadata record) {
        if (!hasOwnerAccessToRecord(record)) {
            logger.warning(String.format("%s has no owner access to %s", headers.getUserEmail(), record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG);
        }
    }

    private void validateViewerAccessToRecord(RecordMetadata record) {
        if (!hasViewerAccessToRecord(record)) {
            logger.warning(String.format("%s has no viewer access to %s", headers.getUserEmail(), record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN,  ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG);
        }
    }
    
    /**
     * Ensures that the ACLs of the record are a subset of the ACLs
     * @param records the records to validate
     */
    // TODO alanbraz: need to reimplement this after Entitlements refactor
    /*
     * Wyatt Nielsen Yesterday at 1:58 PM
     * @Alan Braz [IBM] we raised this as a concern a while back (before we were tracking issues in GitLab).
     * The security model in core is that a user can create a record with an ACL that they don't have access to;
     * and the creator always has access regardless of ACL. We recently fixed this test in GitLab by
     * adding a check to see if the user created the record.
     */
    private void validateRecordAcls(RecordProcessing... records) {
        /*Set<String> validGroups = tenantRepo.findById(headers.getPartitionId())
                .orElseThrow(() -> new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Unknown Tenant", "Tenant was not found"))
                .getGroups()
                .stream()
                .map(group -> group.toLowerCase())
                .collect(Collectors.toSet());
		*/
        //for (RecordProcessing record : records) {
        	//validateOwnerAccessToRecord(record.getRecordMetadata());
            /*for (String acl : record.getRecordMetadata().getAcl().getOwners()) {
                String groupName = acl.split("@")[0].toLowerCase();
                if (!validGroups.contains(groupName)) {
                    throw new AppException(
                            HttpStatus.SC_FORBIDDEN,
                            "Invalid ACL",
                            "Record ACL is not one of " + String.join(",", validGroups));
                }
            }*/
        //}
    }

	@Override
	public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
		// TODO checkDataInconsistency implement
		validateViewerAccessToRecord(record);
		
		String itemName = this.getItemName(record, version);
		logger.info("Reading item: " + itemName);

		return s3Client.getObjectAsString(getBucketName(), itemName);

	}

	@Override
	public Map<String, String> read(Map<String, String> objects) {
		// key -> record id
        // value -> record version path
		Map<String, String> map = new HashMap<>();

        for (Map.Entry<String, String> record : objects.entrySet()) {
            RecordMetadata recordMetadata = recordsMetadataRepository.get(record.getKey());
            if (hasViewerAccessToRecord(recordMetadata))
            	map.put(record.getKey(), getObjectAsString(record.getValue()));
            else
            	map.put(record.getKey(), null);
		}

		return map;
	}

	private String getItemName(RecordMetadata record) {
		return record.getVersionPath(record.getLatestVersion());
	}

	private String getItemName(RecordMetadata record, Long version) {
		return record.getVersionPath(version);
	}

	public String getBucketName() {
		return cosFactory.getBucketName(headers.getPartitionIdWithFallbackToAccountId(), RecordsMetadataRepositoryImpl.DB_NAME);
	}

	private String getObjectAsString(String objectName) {
		return s3Client.getObjectAsString(getBucketName(), objectName);
	}

}
