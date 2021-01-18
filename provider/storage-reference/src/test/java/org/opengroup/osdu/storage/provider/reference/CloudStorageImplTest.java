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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import java.util.HashMap;
import java.util.Map;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.provider.reference.config.MinIoConfigProperties;
import org.opengroup.osdu.storage.provider.reference.factory.CloudObjectStorageFactory;

@RunWith(MockitoJUnitRunner.class)
public class CloudStorageImplTest extends TestCase {

  private RecordProcessing[] recordProcessingArray = new RecordProcessing[1];
  private RecordProcessing recordProcessing;
  private RecordMetadata recordMetadata;
  private String bucketName = "osdu-sample-osdu-file";

  @Mock
  private CloudObjectStorageFactory factory;

  @Mock
  private MinioClient minioClient;

  @Mock
  private MinIoConfigProperties minIoConfigProperties;

  @InjectMocks
  private CloudStorageImpl cloudStorage;

  @Before
  public void setup() {
    recordMetadata = new RecordMetadata();
    recordMetadata.setKind("test-record-id");
    recordMetadata.setId("test-record-id");
    recordMetadata.addGcsPath(1);
    recordMetadata.addGcsPath(2);

    recordProcessing = new RecordProcessing();
    recordProcessing.setRecordMetadata(recordMetadata);

    Record record = new Record();
    record.setId("test-record-id");
    Map<String, Object> data = new HashMap<>();
    data.put("test-data", new Object());
    record.setData(data);

    RecordData recordData = new RecordData(record);
    recordProcessing.setRecordData(recordData);
    recordProcessingArray[0] = recordProcessing;
  }

  @Test
  public void write_test() throws Exception {
    when(minIoConfigProperties.getMinIoBucketRecordName()).thenReturn(bucketName);

    cloudStorage.write(recordProcessingArray);

    verify(minioClient, times(1)).putObject(any());
  }

  @Test
  public void delete_test() throws Exception {
    when(minIoConfigProperties.getMinIoBucketRecordName()).thenReturn(bucketName);

    cloudStorage.delete(recordMetadata);

    verify(minioClient, times(1)).removeObject(any());
  }

  @Test
  public void deleteVersion_test() throws Exception {
    when(minIoConfigProperties.getMinIoBucketRecordName()).thenReturn(bucketName);

    cloudStorage.deleteVersion(recordMetadata, 1L);

    verify(minioClient, times(1)).removeObject(any());
  }

  @Test(expected = AppException.class)
  public void read_with_version_test() throws Exception {
    cloudStorage.read(recordMetadata, 1L, false);

    verify(minioClient, times(1)).getObject(any());
  }

  @Test
  public void read_test() throws Exception {
    when(minIoConfigProperties.getMinIoBucketRecordName()).thenReturn(bucketName);

    Map<String, String> map = new HashMap<>();
    map.put("common:welldb:1", "opendes:ds:mytest1:1.0.0/common:welldb:123456/1603618609515093");
    cloudStorage.read(map);

    verify(minioClient, times(1)).getObject(any());
  }
}