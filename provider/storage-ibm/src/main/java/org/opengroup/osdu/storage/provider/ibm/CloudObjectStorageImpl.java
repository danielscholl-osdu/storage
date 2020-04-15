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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.core.ibm.objectstorage.CloudObjectStorageFactory;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger logger = LoggerFactory.getLogger(CloudObjectStorageImpl.class);

	AmazonS3 cos;
	String bucketName;

	@PostConstruct
    public void init(){
		cos = cosFactory.getClient("records");
		bucketName = cosFactory.getBucketName();
	}

	@Override
	public void write(RecordProcessing... recordsProcessing) {

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

			PutObjectRequest req = new PutObjectRequest(bucketName, itemName, newStream, metadata);
			cos.putObject(req);

			logger.info("Item created!\n" + itemName);

		}

	}
	
	@Override
    public Map<String, String> getHash(Collection<RecordMetadata> records)
    {
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
		String itemName = getItemName(record);
		logger.info("Delete item: " + itemName);
	    cos.deleteObject(bucketName, itemName);
	    logger.info("Item deleted: " + itemName);
	}

	@Override
	public void deleteVersion(RecordMetadata record, Long version) {
		String itemName = getItemName(record, version);
		logger.info("Delete item: " + itemName);
	    cos.deleteObject(bucketName, itemName);
	    logger.info("Item deleted: " + itemName);
	}

	@Override
    public boolean isDuplicateRecord(TransferInfo transfer, Map<String, String> hashMap, Map.Entry<RecordMetadata, RecordData> kv) {
        RecordMetadata updatedRecordMetadata = kv.getKey();
        RecordData recordData = kv.getValue();
        String recordHash = hashMap.get(updatedRecordMetadata.getId());

        String newHash = getHash(recordData);

        if (newHash.equals(recordHash)) {
            transfer.getSkippedRecords().add(updatedRecordMetadata.getId());
            return true;
        }else{
            return false;
        }
    }

	@Override
	public boolean hasAccess(RecordMetadata... records) {
		for (RecordMetadata record : records) {
			if (!record.getStatus().equals(RecordState.active))
				return false;
			if (!cos.doesObjectExist(bucketName, getItemName(record)))
				return false;
		}
		// TODO Implement group validation?!!?
		return true;
	}

	@Override
	public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
		// TODO checkDataInconsistency implement
        String itemName = this.getItemName(record, version);
        logger.info("Reading item: " + itemName);

        return cos.getObjectAsString(bucketName, itemName);

	}

	@Override
	public Map<String, String> read(Map<String, String> objects) {

		logger.info("Reading objects...");

		Map<String, String> map = new HashMap<>();

		for (Entry<String, String> object : objects.entrySet()) {
			String itemName = object.getValue();
			logger.info("Reading item: " + itemName);
			map.put(object.getKey(), cos.getObjectAsString(bucketName, itemName));
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

}
