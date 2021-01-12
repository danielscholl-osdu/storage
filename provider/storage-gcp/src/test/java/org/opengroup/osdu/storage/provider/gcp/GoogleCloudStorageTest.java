package org.opengroup.osdu.storage.provider.gcp;

import com.google.cloud.datastore.Cursor;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.gcp.multitenancy.IStorageFactory;
import org.opengroup.osdu.storage.provider.gcp.config.StorageConfigProperties;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@RunWith(MockitoJUnitRunner.class)
public class GoogleCloudStorageTest {

    private static final String PROJECT_ID = "anyProject";
    private static final String BUCKET = "anyProject-records";
    private static final String SERVICE_ACCOUNT = "test@tenant1.com";
    private static final String KIND = "kind";

    private static final String PATH_1 = "kind/id/123";
    private static final String PATH_2 = "kind/id/456";
    private static final String PATH_3 = "kind/id/789";
    private static final String PATH_3_CHANGED = "kind/id/890";

    private static final String ACL_VIEWER_1 = "v1@tenant1.gmail.com";
    private static final String ACL_VIEWER_2 = "v2@tenant1.gmail.com";
    private static final String ACL_OWNER_1 = "o1@tenant1.gmail.com";
    private static final String ACL_OWNER_2 = "o2@tenant1.gmail.com";

    private Acl acl;

    @Mock
    private DpsHeaders headers;

    @Mock
    private Storage storage;

    @Mock
    private IRecordsMetadataRepository<Cursor> recordRepository;

    @Mock
    private TenantInfo tenant;

    @Mock
    private IStorageFactory storageFactory;

    @Mock
    private JaxRsDpsLog log;

    @Mock
    private ExecutorService threadPool;

    @InjectMocks
    private GoogleCloudStorage sut;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        StorageConfigProperties properties = new StorageConfigProperties();
        properties.setEnableImpersonalization(false);
        sut.setProperties(properties);

        when(this.storageFactory.getStorage(any(), eq(SERVICE_ACCOUNT), eq(PROJECT_ID), any(), any())).thenReturn(this.storage);

        this.acl = new Acl();
        this.acl.setViewers(new String[]{ACL_VIEWER_1, ACL_VIEWER_2});
        this.acl.setOwners(new String[]{ACL_OWNER_1, ACL_OWNER_2});

        when(this.tenant.getProjectId()).thenReturn(PROJECT_ID);
        when(this.tenant.getServiceAccount()).thenReturn(SERVICE_ACCOUNT);

        doAnswer((Answer<Object>) invocation -> {
            List<Callable<?>> tasks = (List<Callable<?>>) invocation.getArguments()[0];
            tasks.forEach(t -> {
                try {
                    t.call();
                } catch (Exception ignored) {
                }
            });
            return Lists.newArrayList();
        }).when(this.threadPool).invokeAll(any(Collection.class));
    }

    @Test
    public void should_updateObjectAcls_provideNewStorageAcls() {
        RecordMetadata metadata1 = new RecordMetadata();
        metadata1.setId("id1");
        metadata1.setKind(KIND);
        metadata1.setAcl(this.acl);
        metadata1.setGcsVersionPaths(Lists.newArrayList(PATH_1));

        RecordMetadata metadata2 = new RecordMetadata();
        metadata2.setId("id2");
        metadata2.setKind(KIND);
        metadata2.setAcl(this.acl);
        metadata2.setGcsVersionPaths(Lists.newArrayList(PATH_2));

        RecordMetadata metadata3 = new RecordMetadata();
        metadata3.setId("id3");
        metadata3.setKind(KIND);
        metadata3.setAcl(this.acl);
        metadata3.setGcsVersionPaths(Lists.newArrayList(PATH_3));

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(metadata1);
        recordMetadataList.add(metadata2);
        recordMetadataList.add(metadata3);

        Blob blob = mock(Blob.class);
        when(this.storage.get(BUCKET, PATH_1)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_2)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_3)).thenReturn(blob);

        Blob.Builder blobBuilder = mock(Blob.Builder.class, RETURNS_DEEP_STUBS);
        when(blob.toBuilder()).thenReturn(blobBuilder);
        when(blobBuilder.setAcl(any())).thenReturn(blobBuilder);

        List<String> recordsId = new ArrayList<>();
        recordsId.add("id1");
        recordsId.add("id2");
        recordsId.add("id3");

        List<String> lockedRecords = new ArrayList<>();
        List<RecordMetadata> validMetadata = new ArrayList<>();

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id1", metadata1);
        currentRecords.put("id2", metadata2);
        currentRecords.put("id3", metadata3);

        Map<String, String> idMap = new HashMap<>();
        idMap.put("id1", "id1");
        idMap.put("id2", "id2");
        idMap.put("id3", "id3");

        when(this.recordRepository.get(recordsId)).thenReturn(currentRecords);
        Map<String, Acl> originalAcls = this.sut.updateObjectMetadata(recordMetadataList, recordsId, validMetadata, lockedRecords, idMap);
        assertEquals(3, originalAcls.size());
        assertEquals(0, lockedRecords.size());
        verify(blobBuilder, times(3)).setAcl(any());
    }

    @Test
    public void should_passLockedRecord_whenOneRecordVersionChanged() {
        RecordMetadata metadata1 = new RecordMetadata();
        metadata1.setId("id1:unit:test");
        metadata1.setKind(KIND);
        metadata1.setAcl(this.acl);
        metadata1.setGcsVersionPaths(Lists.newArrayList(PATH_1));

        RecordMetadata metadata2 = new RecordMetadata();
        metadata2.setId("id2:unit:test");
        metadata2.setKind(KIND);
        metadata2.setAcl(this.acl);
        metadata2.setGcsVersionPaths(Lists.newArrayList(PATH_2));

        RecordMetadata metadata3 = new RecordMetadata();
        metadata3.setId("id3:unit:test");
        metadata3.setKind(KIND);
        metadata3.setAcl(this.acl);
        metadata3.setGcsVersionPaths(Lists.newArrayList(PATH_3));

        RecordMetadata metadata3changed = new RecordMetadata();
        metadata3changed.setId("id3:unit:test");
        metadata3changed.setKind(KIND);
        metadata3changed.setAcl(this.acl);
        metadata3changed.setGcsVersionPaths(Lists.newArrayList(PATH_3_CHANGED));

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(metadata1);
        recordMetadataList.add(metadata2);
        recordMetadataList.add(metadata3);

        Blob blob = mock(Blob.class);
        when(this.storage.get(BUCKET, PATH_1)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_2)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_3)).thenReturn(blob);

        Blob.Builder blobBuilder = mock(Blob.Builder.class, RETURNS_DEEP_STUBS);
        when(blob.toBuilder()).thenReturn(blobBuilder);
        when(blobBuilder.setAcl(any())).thenReturn(blobBuilder);

        List<String> recordsId = new ArrayList<>();
        recordsId.add("id1:unit:test:123");
        recordsId.add("id2:unit:test:456");
        recordsId.add("id3:unit:test:789");

        List<String> lockedRecords = new ArrayList<>();
        List<RecordMetadata> validMetadata = new ArrayList<>();

        Map<String, RecordMetadata> currentRecords = new HashMap<>();
        currentRecords.put("id1:unit:test", metadata1);
        currentRecords.put("id2:unit:test", metadata2);
        currentRecords.put("id3:unit:test", metadata3changed);

        Map<String, String> idMap = new HashMap<>();
        idMap.put("id1:unit:test", "id1:unit:test:123");
        idMap.put("id2:unit:test", "id2:unit:test:456");
        idMap.put("id3:unit:test", "id3:unit:test:789");

        when(this.recordRepository.get(anyList())).thenReturn(currentRecords);
        Map<String, Acl> originalAcls = this.sut.updateObjectMetadata(recordMetadataList, recordsId, validMetadata, lockedRecords, idMap);
        assertEquals(2, originalAcls.size());
        assertEquals(1, lockedRecords.size());
        assertEquals(2, validMetadata.size());
        verify(blobBuilder, times(2)).setAcl(any());
    }

    @Test
    public void should_revertObjectAcls_provideOriginalAclMap() {
        RecordMetadata metadata1 = new RecordMetadata();
        metadata1.setId("id1");
        metadata1.setKind(KIND);
        metadata1.setAcl(this.acl);
        metadata1.setGcsVersionPaths(Lists.newArrayList(PATH_1));

        RecordMetadata metadata2 = new RecordMetadata();
        metadata2.setId("id2");
        metadata2.setKind(KIND);
        metadata2.setAcl(this.acl);
        metadata2.setGcsVersionPaths(Lists.newArrayList(PATH_2));

        RecordMetadata metadata3 = new RecordMetadata();
        metadata3.setId("id3");
        metadata3.setKind(KIND);
        metadata3.setAcl(this.acl);
        metadata3.setGcsVersionPaths(Lists.newArrayList(PATH_3));

        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        recordMetadataList.add(metadata1);
        recordMetadataList.add(metadata2);
        recordMetadataList.add(metadata3);

        Map<String, Acl> originalAcls = new HashMap<>();
        originalAcls.put("id1", this.acl);
        originalAcls.put("id2", this.acl);
        originalAcls.put("id3", this.acl);

        Blob blob = mock(Blob.class);
        when(this.storage.get(BUCKET, PATH_1)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_2)).thenReturn(blob);
        when(this.storage.get(BUCKET, PATH_3)).thenReturn(blob);

        Blob.Builder blobBuilder = mock(Blob.Builder.class, RETURNS_DEEP_STUBS);
        when(blob.toBuilder()).thenReturn(blobBuilder);
        when(blobBuilder.setAcl(any())).thenReturn(blobBuilder);

        this.sut.revertObjectMetadata(recordMetadataList, originalAcls);
        verify(blobBuilder, times(3)).setAcl(any());
    }
}