/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.reference;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.TransferInfo;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.reference.config.MinIoConfigProperties;
import org.opengroup.osdu.storage.provider.reference.factory.CloudObjectStorageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;

@Repository
public class CloudStorageImpl implements ICloudStorage {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudStorageImpl.class);

  private final MinIoConfigProperties minIoConfigProperties;
  private final CloudObjectStorageFactory factory;

  private MinioClient minioClient;

  public CloudStorageImpl(CloudObjectStorageFactory factory,
      MinIoConfigProperties minIoConfigProperties) {
    this.factory = factory;
    this.minIoConfigProperties = minIoConfigProperties;
  }

  @PostConstruct
  public void init() {
    minioClient = factory.getClient();
  }

  @Override
  public void write(RecordProcessing... recordsProcessing) {
    Gson gson = new GsonBuilder().serializeNulls().create();
    for (RecordProcessing rp : recordsProcessing) {
      Map<String, String> headers = new HashMap<>();
      headers.put("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE);
      headers.put("X-Amz-Storage-Class", "REDUCED_REDUNDANCY");

      String content = gson.toJson(rp.getRecordData());
      byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
      String itemName = getItemName(rp.getRecordMetadata()).replace(":", "-");
      try {
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket(minIoConfigProperties.getMinIoBucketRecordName())
                .object(itemName)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .headers(headers)
                .build());
      } catch (Exception e) {
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR,
            "Failed to write new record.", e.getMessage());
      }
    }
  }

  @Override
  public Map<String, String> getHash(Collection<RecordMetadata> records) {
    Gson gson = new Gson();
    Map<String, String> hashes = new HashMap<>();
    for (RecordMetadata rm : records) {
      String jsonData = read(rm, rm.getLatestVersion(), false);
      RecordData data = gson.fromJson(jsonData, RecordData.class);

      String hash = getHash(data);
      hashes.put(rm.getId(), hash);
    }
    return hashes;
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

  private String getItemName(RecordMetadata record) {
    return record.getVersionPath(record.getLatestVersion());
  }

  private String getItemName(RecordMetadata record, Long version) {
    return record.getVersionPath(version);
  }

  @Override
  public void delete(RecordMetadata record) {
    String itemName = getItemName(record).replace(":", "-");
    try {
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(minIoConfigProperties.getMinIoBucketRecordName())
              .object(itemName)
              .build());
    } catch (Exception e) {
      throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to delete record.",
          e.getMessage());
    }
  }

  @Override
  public void deleteVersion(RecordMetadata record, Long version) {
    String itemName = getItemName(record, version).replace(":", "-");
    try {
      if (!record.hasVersion()) {
          LOGGER.warn(String.format("Record %s does not have versions available", record.getId()));
      }
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(minIoConfigProperties.getMinIoBucketRecordName())
              .object(itemName)
              .build());
    } catch (Exception e) {
      throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to delete version.",
          e.getMessage());
    }
  }

  @Override
  public boolean hasAccess(RecordMetadata... records) {
    for (RecordMetadata record : records) {
      if (!record.getStatus().equals(RecordState.active)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String read(RecordMetadata record, Long version, boolean checkDataInconsistency) {
    String itemName = getItemName(record, version).replace(":", "-");
    String msg = String
        .format("Record with id '%s' does not exist, version: %s", record.getId(), version);
    InputStream stream;
    try {
      stream = minioClient.getObject(
          GetObjectArgs.builder()
              .bucket(minIoConfigProperties.getMinIoBucketRecordName())
              .object(itemName)
              .build());
      if (stream == null) {
          LOGGER.warn(msg);
      } else {
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
            .collect(Collectors.joining("\n"));
      }
    } catch (Exception e) {
      throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to get object.",
          e.getMessage());
    }
    throw new AppException(HttpStatus.SC_NOT_FOUND, "Record not found", msg);
  }

  @Override
  public Map<String, String> read(Map<String, String> objects) {
    // key -> record id
    // value -> record version path
    Map<String, String> map = new HashMap<>();
    for (Map.Entry<String, String> record : objects.entrySet()) {
      String[] tokens = record.getValue().split("/");
      String key = tokens[tokens.length - 2];
      try {
        InputStream stream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(minIoConfigProperties.getMinIoBucketRecordName())
                .object(record.getValue().replace(":", "-"))
                .build());
        if (stream != null) {
          String result = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .lines()
              .collect(Collectors.joining("\n"));
          map.put(key, result);
        }
      } catch (Exception e) {
        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to get object.",
            e.getMessage());
      }
    }
    return map;
  }
}
