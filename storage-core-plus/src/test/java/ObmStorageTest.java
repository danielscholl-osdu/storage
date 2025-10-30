import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.partition.PartitionPropertyResolver;
import org.opengroup.osdu.core.obm.core.Driver;
import org.opengroup.osdu.storage.provider.gcp.web.config.PartitionPropertyNames;
import org.opengroup.osdu.storage.provider.gcp.web.repository.ObmStorage;

@ExtendWith(MockitoExtension.class)
class ObmStorageTest {

  private static final String PARTITION_ID = "dummyPartitionID";
  private static final String BUCKET_NAME = "dummy_bucket";
  private static final List<String> VERSION_PATHS = Arrays.asList("versionPath1", "versionPath2");

  @InjectMocks
  private ObmStorage storage;

  @Mock
  private Driver storageDriver;

  @Mock
  private PartitionPropertyNames partitionPropertyNames;

  @Mock
  private TenantInfo tenantInfo;

  @Mock
  private PartitionPropertyResolver partitionPropertyResolver;


  @Test
  void deleteSeveralVersions() {
    when(partitionPropertyNames.getStorageBucketName()).thenReturn(BUCKET_NAME);
    when(tenantInfo.getDataPartitionId()).thenReturn(PARTITION_ID);

    storage.deleteVersions(VERSION_PATHS);

    VERSION_PATHS.forEach(versionPath ->
        verify(storageDriver, times(1))
            .deleteBlob(any() , eq(versionPath), any()));
  }
}
