package org.opengroup.osdu.storage.service;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.RecordVersions;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.model.GetRecordsModel;
import org.opengroup.osdu.storage.model.RecordInfoQueryResult;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;

@ExtendWith(MockitoExtension.class)
public class QueryServiceImplTest {

    private static final String RECORD_ID = "opendes:doc:123";
    private static final String TENANT_NAME = "opendes";
    private static final String KIND = "opendes:wks:doc:1.0.0";

    @Mock
    private IRecordsMetadataRepository recordRepository;
    @Mock
    private ICloudStorage cloudStorage;
    @Mock
    private TenantInfo tenant;
    @Mock
    private StorageAuditLogger auditLogger;
    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private DataAuthorizationService dataAuthorizationService;

    @InjectMocks
    private QueryServiceImpl sut;

    private Optional<CollaborationContext> emptyCollaboration = Optional.empty();

    @BeforeEach
    public void setup() {
        lenient().when(tenant.getName()).thenReturn(TENANT_NAME);
    }

    @Test
    public void getRecordInfo_shouldReturnRecord_whenRecordExistsAndUserHasAccess() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 3L, true)).thenReturn("{\"data\":\"value\"}");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(true);

        String result = sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false);

        assertNotNull(result);
        verify(auditLogger).readLatestVersionOfRecordSuccess(singletonList(RECORD_ID));
    }

    @Test
    public void getRecordInfo_shouldAuditFail_whenAppExceptionThrown() {
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.getError().getCode());
        verify(auditLogger).readLatestVersionOfRecordFail(singletonList(RECORD_ID));
    }

    @Test
    public void getRecordInfoByVersion_shouldReturnRecord_whenVersionExists() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 2L, true)).thenReturn("{\"data\":\"value\"}");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(true);

        String result = sut.getRecordInfo(RECORD_ID, 2L, new String[]{}, emptyCollaboration);

        assertNotNull(result);
        verify(auditLogger).readSpecificVersionOfRecordSuccess(singletonList(RECORD_ID));
    }

    @Test
    public void getRecordInfoByVersion_shouldAuditFail_whenAppExceptionThrown() {
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(null);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, 1L, new String[]{}, emptyCollaboration));

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.getError().getCode());
        verify(auditLogger).readSpecificVersionOfRecordFail(singletonList(RECORD_ID));
    }

    @Test
    public void listVersions_shouldReturnVersions_whenRecordExistsAndUserHasAccess() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(dataAuthorizationService.hasAccess(metadata, OperationType.view)).thenReturn(true);

        RecordVersions versions = sut.listVersions(RECORD_ID, emptyCollaboration);

        assertEquals(RECORD_ID, versions.getRecordId());
        assertEquals(3, versions.getVersions().size());
        assertTrue(versions.getVersions().contains(1L));
        assertTrue(versions.getVersions().contains(2L));
        assertTrue(versions.getVersions().contains(3L));
        verify(auditLogger).readAllVersionsOfRecordSuccess(singletonList(RECORD_ID));
    }

    @Test
    public void listVersions_shouldThrowForbidden_whenUserLacksAccess() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(dataAuthorizationService.hasAccess(metadata, OperationType.view)).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> sut.listVersions(RECORD_ID, emptyCollaboration));

        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getError().getCode());
        verify(auditLogger).readAllVersionsOfRecordFail(singletonList(RECORD_ID));
    }

    @Test
    public void getRecordInfo_shouldThrowBadRequest_whenRecordIdDoesNotBelongToTenant() {
        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo("wrongtenant:doc:123", new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getError().getCode());
    }

    @Test
    public void getRecordInfo_shouldThrowNotFound_whenRecordHasNoVersions() {
        RecordMetadata metadata = new RecordMetadata();
        metadata.setId(RECORD_ID);
        metadata.setKind(KIND);
        metadata.setStatus(RecordState.active);
        metadata.setGcsVersionPaths(Collections.emptyList());
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.getError().getCode());
    }

    @Test
    public void getRecordInfo_shouldThrowNotFound_whenRecordIsInactiveAndFetchInactiveIsFalse() {
        RecordMetadata metadata = buildActiveMetadata();
        metadata.setStatus(RecordState.deleted);
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.getError().getCode());
    }

    @Test
    public void getRecordInfo_shouldReturnRecord_whenRecordIsInactiveAndFetchInactiveIsTrue() {
        RecordMetadata metadata = buildActiveMetadata();
        metadata.setStatus(RecordState.deleted);
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 3L, true)).thenReturn("{\"data\":\"value\"}");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(true);

        String result = sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, true);

        assertNotNull(result);
    }

    @Test
    public void getRecordInfo_shouldThrowForbidden_whenUserLacksViewAccess() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 3L, true)).thenReturn("{\"data\":\"value\"}");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(false);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_FORBIDDEN, ex.getError().getCode());
    }

    @Test
    public void getRecordInfo_shouldThrowNotFound_whenBlobIsEmpty() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 3L, true)).thenReturn("");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecordInfo(RECORD_ID, new String[]{}, emptyCollaboration, false));

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.getError().getCode());
    }

    @Test
    public void getRecordInfo_shouldFilterAttributes_whenAttributesProvided() {
        RecordMetadata metadata = buildActiveMetadata();
        when(recordRepository.get(RECORD_ID, emptyCollaboration)).thenReturn(metadata);
        when(cloudStorage.read(metadata, 3L, true)).thenReturn("{\"data\":{\"name\":\"test\",\"age\":10}}");
        when(dataAuthorizationService.validateViewerOrOwnerAccess(eq(metadata), eq(OperationType.view))).thenReturn(true);

        String result = sut.getRecordInfo(RECORD_ID, new String[]{"data.name"}, emptyCollaboration, false);

        assertNotNull(result);
        assertTrue(result.contains("\"name\""), "Filtered result should contain requested attribute 'name'");
        assertFalse(result.contains("\"age\""), "Filtered result should not contain non-requested attribute 'age'");
    }

    @Test
    public void getRecords_shouldReturnEmptyResults_whenNoRecordsFound() {
        GetRecordsModel model = GetRecordsModel.builder().kind(KIND).limit(10).build();
        RecordInfoQueryResult<RecordMetadata> emptyResult = new RecordInfoQueryResult<>("cursor", Collections.emptyList());
        when(recordRepository.getRecords(eq(KIND), isNull(), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration)))
                .thenReturn(emptyResult);

        RecordInfoQueryResult<Record> result = sut.getRecords(model, null, emptyCollaboration);

        assertNotNull(result);
        assertTrue(result.getResults().isEmpty());
        assertEquals("cursor", result.getCursor());
    }

    @Test
    public void getRecords_shouldReturnRecords_whenRecordsExist() {
        GetRecordsModel model = GetRecordsModel.builder().kind(KIND).limit(10).build();
        RecordMetadata metadata = buildActiveMetadata();
        RecordInfoQueryResult<RecordMetadata> metadataResult = new RecordInfoQueryResult<>("nextCursor", singletonList(metadata));
        when(recordRepository.getRecords(eq(KIND), isNull(), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration)))
                .thenReturn(metadataResult);

        Map<String, String> recordData = new HashMap<>();
        recordData.put(RECORD_ID, "{\"data\":{\"name\":\"test\"}}");
        when(cloudStorage.read(anyMap(), eq(emptyCollaboration))).thenReturn(recordData);

        RecordInfoQueryResult<Record> result = sut.getRecords(model, null, emptyCollaboration);

        assertNotNull(result);
        assertFalse(result.getResults().isEmpty());
        assertEquals("nextCursor", result.getCursor());
    }

    @Test
    public void getRecords_shouldLogWarning_whenRecordDataIsEmpty() {
        GetRecordsModel model = GetRecordsModel.builder().kind(KIND).limit(10).build();
        RecordMetadata metadata = buildActiveMetadata();
        RecordInfoQueryResult<RecordMetadata> metadataResult = new RecordInfoQueryResult<>("cursor", singletonList(metadata));
        when(recordRepository.getRecords(eq(KIND), isNull(), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration)))
                .thenReturn(metadataResult);

        Map<String, String> recordData = new HashMap<>();
        recordData.put(RECORD_ID, "");
        when(cloudStorage.read(anyMap(), eq(emptyCollaboration))).thenReturn(recordData);

        RecordInfoQueryResult<Record> result = sut.getRecords(model, null, emptyCollaboration);

        assertTrue(result.getResults().isEmpty());
        verify(logger).warning(contains("No data found for record"));
    }

    @Test
    public void getRecords_shouldThrowInternalError_whenUnexpectedExceptionOccurs() {
        GetRecordsModel model = GetRecordsModel.builder().kind(KIND).limit(10).build();
        when(recordRepository.getRecords(eq(KIND), isNull(), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration)))
                .thenThrow(new RuntimeException("Unexpected"));

        AppException ex = assertThrows(AppException.class,
                () -> sut.getRecords(model, null, emptyCollaboration));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getError().getCode());
    }

    @Test
    public void getRecords_shouldUseModifiedAfterDate_whenProvided() {
        Date modifiedDate = new Date();
        GetRecordsModel model = GetRecordsModel.builder().kind(KIND).limit(10).modifiedAfterDate(modifiedDate).build();
        RecordInfoQueryResult<RecordMetadata> emptyResult = new RecordInfoQueryResult<>("cursor", Collections.emptyList());
        when(recordRepository.getRecords(eq(KIND), eq(modifiedDate.getTime()), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration)))
                .thenReturn(emptyResult);

        RecordInfoQueryResult<Record> result = sut.getRecords(model, null, emptyCollaboration);

        assertNotNull(result);
        verify(recordRepository).getRecords(eq(KIND), eq(modifiedDate.getTime()), isNull(), eq(10), eq(false), isNull(), eq(emptyCollaboration));
    }

    private RecordMetadata buildActiveMetadata() {
        RecordMetadata metadata = new RecordMetadata();
        metadata.setId(RECORD_ID);
        metadata.setKind(KIND);
        metadata.setStatus(RecordState.active);
        metadata.setGcsVersionPaths(Arrays.asList(
                RECORD_ID + "/1",
                RECORD_ID + "/2",
                RECORD_ID + "/3"
        ));
        return metadata;
    }
}





