package org.opengroup.osdu.storage.provider.azure;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.azure.blobstorage.BlobStore;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class CloudStorageImplTest {

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private EntitlementsAndCacheServiceAzure entitlementsAndCacheServiceAzure;

    @Mock
    private DpsHeaders headers;

    @Mock
    private BlobStore blobStore;

    @InjectMocks
    private CloudStorageImpl cloudStorage;

    private static final String DATA_PARTITION = "dp";
    private static final String CONTAINER = "opendes";

    @Before
    public void setup() {
        ReflectionTestUtils.setField(cloudStorage, "containerName", CONTAINER);
        Mockito.when(headers.getPartitionId()).thenReturn(DATA_PARTITION);
    }

    @Test
    public void shouldNotInvokeDeleteOnBlobStoreWhenNoVersion() {
        RecordMetadata recordMetadata = setUpRecordMetadata();
        cloudStorage.delete(recordMetadata);
        Mockito.verify(blobStore, Mockito.never()).deleteFromStorageContainer(any(String.class), any(String.class), any(String.class));
    }

    @Test(expected = AppException.class)
    public void shouldThrowAppExceptionWhenDeleteWithNoOwnerAccess() {
        RecordMetadata recordMetadata = setUpRecordMetadata();
        recordMetadata.setGcsVersionPaths(Arrays.asList("path1", "path2"));
        Mockito.when(entitlementsAndCacheServiceAzure.hasAccessToData(any(DpsHeaders.class), any(Set.class))).thenReturn(false);
        cloudStorage.delete(recordMetadata);
    }

    @Test
    public void shouldDeleteAllVersionsFromBlobStoreUponDeleteAction() {
        RecordMetadata recordMetadata = setUpRecordMetadata();
        recordMetadata.setGcsVersionPaths(Arrays.asList("path1", "path2"));
        Mockito.when(entitlementsAndCacheServiceAzure.hasAccessToData(any(DpsHeaders.class), any(Set.class))).thenReturn(true);
        cloudStorage.delete(recordMetadata);
        Mockito.verify(blobStore, Mockito.times(1)).deleteFromStorageContainer(DATA_PARTITION, "path1", CONTAINER);
        Mockito.verify(blobStore, Mockito.times(1)).deleteFromStorageContainer(DATA_PARTITION, "path2", CONTAINER);

    }

    private RecordMetadata setUpRecordMetadata() {
        Record record = new Record();
        record.setId("id1");
        Acl acl = Acl.builder()
                .viewers(new String[]{"viewers"})
                .owners(new String[]{"owners"})
                .build();
        record.setAcl(acl);
        record.setKind("kind");
        Legal legal = new Legal();
        legal.setStatus(LegalCompliance.compliant);
        legal.setLegaltags(Sets.newHashSet("legalTag1"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("US"));
        record.setLegal(legal);
        record.setVersion(1L);
        RecordMetadata recordMetadata = new RecordMetadata(record);
        return recordMetadata;
    }
}
