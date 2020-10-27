package org.opengroup.osdu.storage.provider.reference;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
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
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordData;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.storage.provider.reference.factory.CloudObjectStorageFactory;

@RunWith(MockitoJUnitRunner.class)
public class CloudStorageImplTest extends TestCase {

  @Mock
  private CloudObjectStorageFactory factory;

  @Mock
  private MinioClient minioClient;

  @Mock
  private JaxRsDpsLog log;

  @InjectMocks
  private CloudStorageImpl cloudStorage;

  private RecordProcessing[] recordProcessings = new RecordProcessing[1];
  private RecordProcessing recordProcessing;
  private RecordMetadata recordMetadata;

  @Before
  public void setup() {
    initMocks(this);

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
    recordProcessings[0] = recordProcessing;
    cloudStorage.setRecordBucketName("record-bucket");
  }

  @Test
  public void write_test() throws Exception {
    cloudStorage.write(recordProcessings);
    verify(minioClient, times(1)).putObject(any());
  }

  @Test
  public void delete_test() throws Exception {
    cloudStorage.delete(recordMetadata);
    verify(minioClient, times(1)).removeObject(any());
  }

  @Test
  public void deleteVersion_test() throws Exception {
    cloudStorage.deleteVersion(recordMetadata, (long) 1);
    verify(minioClient, times(1)).removeObject(any());
  }

  @Test(expected = AppException.class)
  public void read_with_version_test() throws Exception {
    cloudStorage.read(recordMetadata, (long) 1, false);
    verify(minioClient, times(1)).getObject(any());
  }

  @Test
  public void read_test() throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("common:welldb:1", "opendes:ds:mytest1:1.0.0/common:welldb:123456/1603618609515093");
    cloudStorage.read(map);
    verify(minioClient, times(1)).getObject(any());
  }
}