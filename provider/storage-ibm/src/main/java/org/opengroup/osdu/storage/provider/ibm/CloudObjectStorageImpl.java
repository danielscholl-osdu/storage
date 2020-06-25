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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.ibm.objectstorage.CloudObjectStorageFactory;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.minio.MinioClient;
import io.minio.PutObjectOptions;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.RegionConflictException;
import io.minio.errors.XmlParserException;

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

	// @Inject
	// private ITenantFactory tenant;
	// TODO use tenant name at the bucket name
	
	private static final Logger logger = LoggerFactory.getLogger(CloudObjectStorageImpl.class);

	String bucketName;
	MinioClient minioClient;

	@PostConstruct
	public void init() {
		try {
			minioClient = cosFactory.getClient("records");
		} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException
				| InternalException | InvalidBucketNameException | InvalidResponseException | NoSuchAlgorithmException
				| XmlParserException | RegionConflictException | IOException e) {
			e.printStackTrace();
		}
		bucketName = cosFactory.getBucketName();
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

			try {
				minioClient.putObject(bucketName, itemName, newStream, new PutObjectOptions(bytesSize, -1));
			} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException
					| InsufficientDataException | InternalException | InvalidBucketNameException
					| InvalidResponseException | NoSuchAlgorithmException | XmlParserException | IOException e) {
				e.printStackTrace();
			}

			logger.info("Item created in minio bucket!\n" + itemName);

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
		logger.info("Delete item: " + itemName);
		deleteObject(bucketName, itemName);
		logger.info("Item deleted: " + itemName);
	}

	@Override
	public void deleteVersion(RecordMetadata record, Long version) {
		validateOwnerAccessToRecord(record);
		String itemName = getItemName(record, version);
		logger.info("Delete item: " + itemName);
		deleteObject(bucketName, itemName);
		logger.info("Item deleted: " + itemName);
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
            logger.warn(String.format("%s has no owner access to %s", headers.getUserEmail(), record.getId()));
            throw new AppException(HttpStatus.SC_FORBIDDEN, ACCESS_DENIED_ERROR_REASON, ACCESS_DENIED_ERROR_MSG);
        }
    }

    private void validateViewerAccessToRecord(RecordMetadata record) {
        if (!hasViewerAccessToRecord(record)) {
            logger.warn(String.format("%s has no viewer access to %s", headers.getUserEmail(), record.getId()));
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

		return getObjectAsString(itemName);

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

	// TODO how to get Tenant here??
	public String getBucketName(TenantInfo tenant) {
		return String.format("%s-%s-records", bucketName, tenant.getProjectId()).toLowerCase();
	}


	private void deleteObject(String bucketName, String itemName) {
		try {
			minioClient.removeObject(bucketName, itemName);
			logger.info("Item deleted: " + itemName);
		} catch (Exception  e) {
		    logger.error("Failed to delete item " +itemName);
		}
	}

	private String getObjectAsString(String objectName) {
		try {
			return getContentAsString(minioClient.getObject(bucketName, objectName));
		} catch (InvalidKeyException | ErrorResponseException | IllegalArgumentException | InsufficientDataException
				| InternalException | InvalidBucketNameException | InvalidResponseException | NoSuchAlgorithmException
				| XmlParserException | IOException e) {
			logger.error("Failed to read item " + objectName + " from " + bucketName + " bucket." );
			return "";
		}
	}

	 private String getContentAsString(InputStream input) throws IOException {
	        // Read the text input stream one line at a time and display each line.
	        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
	        StringBuffer content = new StringBuffer();
	        String line = null;
	        while ((line = reader.readLine()) != null) {
	            content.append(line);
	        }
	        input.close();
	        return content.toString();
	    }


}
