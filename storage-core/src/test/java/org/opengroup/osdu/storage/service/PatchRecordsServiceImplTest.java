package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.MultiRecordIds;
import org.opengroup.osdu.core.common.model.storage.MultiRecordInfo;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PatchRecordsServiceImplTest {

    private static final String RECORD_ID1 = "tenant:record:Id1";
    private static final String RECORD_ID2 = "tenant:record:Id2";
    private static final List<String> RECORD_IDS = Arrays.asList(RECORD_ID1, RECORD_ID2);
    private static final String[] OWNERS = new String[]{"owner1@slb.com", "owner2@slb.com"};
    private static final String[] VIEWERS = new String[]{"viewer1@slb.com", "viewer2@slb.com"};
    private static final String USER = "user";
    private static final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.empty();

    @Mock
    RecordUtil recordUtil;
    @Mock
    PatchInputValidator patchInputValidator;
    @Mock
    IRecordsMetadataRepository recordRepository;
    @Mock
    IOPAService opaService;
    @Mock
    PersistenceService persistenceService;
    @Mock
    EntitlementsAndCacheServiceImpl entitlementsAndCacheService;
    @Mock
    DpsHeaders headers;
    @Mock
    StorageAuditLogger auditLogger;
    @Mock
    BatchService batchService;
    @Mock
    IngestionService ingestionService;

    @InjectMocks
    PatchRecordsServiceImpl sut;

    private final ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setup() throws IOException {

    }

    @Test
    public void shouldSuccessfullyPatchRecordData() throws IOException {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(RECORD_IDS, result.getRecordIds());
        Record patchedRecord1 = getRecord(RECORD_ID1);
        patchedRecord1.setData(Collections.singletonMap("Hello", "world"));
        Record patchedRecord2 = getRecord(RECORD_ID2);
        patchedRecord2.setData(Collections.singletonMap("Hello", "world"));
        verify(ingestionService).createUpdateRecords(false, Arrays.asList(patchedRecord1, patchedRecord2), USER, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldSuccessfullyPatchRecordMetaData_whenOpaIsEnabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        when(recordRepository.get(RECORD_IDS, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        List<RecordMetadata> recordMetadataList = new ArrayList<>(existingRecords.values());
        when(opaService.validateUserAccessToRecords(recordMetadataList, OperationType.update)).thenReturn(Collections.emptyList());
        List<RecordMetadata> recordMetadataToBePatched = new ArrayList<>();
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID1));
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID2));

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(RECORD_IDS, result.getRecordIds());
        verify(persistenceService).patchRecordsMetadata(recordMetadataToBePatched, jsonPatch, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());

    }

    @Test
    public void shouldSuccessfullyPatchRecordMetaData_whenOpaIsDisabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        when(recordRepository.get(RECORD_IDS, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        when(entitlementsAndCacheService.hasOwnerAccess(eq(headers), any())).thenReturn(true);
        List<RecordMetadata> recordMetadataToBePatched = new ArrayList<>();
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID1));
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID2));

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        verify(patchInputValidator).validateLegalTags(jsonPatch);
        verify(entitlementsAndCacheService, times(2)).hasOwnerAccess(headers, OWNERS);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(RECORD_IDS, result.getRecordIds());
        verify(persistenceService).patchRecordsMetadata(recordMetadataToBePatched, jsonPatch, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    private MultiRecordInfo getMultiRecordInfo() {
        MultiRecordInfo multiRecordInfo = new MultiRecordInfo();
        Record record1 = getRecord(RECORD_ID1);
        Record record2 = getRecord(RECORD_ID2);

        multiRecordInfo.setRecords(Arrays.asList(record1, record2));
        multiRecordInfo.setInvalidRecords(new ArrayList<>());
        multiRecordInfo.setRetryRecords(new ArrayList<>());
        return multiRecordInfo;
    }

    private static Record getRecord(String recordId) {
        Record record = new Record();
        record.setId(recordId);
        record.setAcl(new Acl(VIEWERS, OWNERS));
        return record;
    }

    private Map<String, RecordMetadata> getExistingRecordsMetadata() {
        RecordMetadata recordMetadata1 = getRecordMetadata(RECORD_ID1);
        RecordMetadata recordMetadata2 = getRecordMetadata(RECORD_ID2);

        Map<String, RecordMetadata> existingRecords = new HashMap<>();
        existingRecords.put(RECORD_ID1, recordMetadata1);
        existingRecords.put(RECORD_ID2, recordMetadata2);
        return existingRecords;
    }

    private static RecordMetadata getRecordMetadata(String recordId) {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(recordId);
        recordMetadata.setAcl(new Acl(VIEWERS, OWNERS));
        return recordMetadata;
    }

    private void verifyValidatorsWereInvocated(JsonPatch jsonPatch) {
        verify(recordUtil).validateRecordIds(RECORD_IDS);
        verify(patchInputValidator).validateDuplicates(jsonPatch);
        verify(patchInputValidator).validateAcls(jsonPatch);
        verify(patchInputValidator).validateKind(jsonPatch);
        verify(patchInputValidator).validateAncestry(jsonPatch);
    }
}
