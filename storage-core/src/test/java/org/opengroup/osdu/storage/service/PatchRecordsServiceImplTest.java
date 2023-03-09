package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatchRecordsServiceImplTest {

    private static final String RECORD_ID1 = "tenant:record:Id1";
    private static final String RECORD_ID2 = "tenant:record:Id2";
    private static final List<String> RECORD_IDS = new LinkedList<>(Arrays.asList(RECORD_ID1, RECORD_ID2));
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
    private IOPAService opaService;
    @Mock
    PersistenceService persistenceService;
    @Mock
    EntitlementsAndCacheServiceImpl entitlementsAndCacheService;
    @Mock
    DpsHeaders headers;
    @Mock
    StorageAuditLogger auditLogger;
    @Mock
    private BatchService batchService;
    @Mock
    private IngestionService ingestionService;
    @Mock
    JaxRsDpsLog logger;

    @InjectMocks
    private PatchRecordsServiceImpl sut;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSuccessfullyPatchRecordData() throws IOException {
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
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
        assertEquals(RECORD_IDS, result.getRecordIds());
        verify(persistenceService).patchRecordsMetadata(recordMetadataToBePatched, jsonPatch, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());

    }

    @Test
    @Ignore("is passes separately, but not together with other tests")
    public void shouldSuccessfullyPatchRecordMetaData_whenOpaIsDisabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        when(recordRepository.get(RECORD_IDS, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        when(entitlementsAndCacheService.hasOwnerAccess(headers, OWNERS)).thenReturn(true);
        List<RecordMetadata> recordMetadataToBePatched = new ArrayList<>();
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID1));
        recordMetadataToBePatched.add(existingRecords.get(RECORD_ID2));

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        verify(patchInputValidator).validateLegalTags(jsonPatch);
        verify(entitlementsAndCacheService, times(2)).hasOwnerAccess(headers, OWNERS);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(RECORD_IDS, result.getRecordIds());
        verify(persistenceService).patchRecordsMetadata(recordMetadataToBePatched, jsonPatch, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldFailRecordDataPatch_ifValueInvalid() throws IOException {
        JsonPatch inValidJsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":\"inValidDataFormat\"}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, inValidJsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(inValidJsonPatch);
        assertThat(result.getFailedRecordIds(), containsInAnyOrder(RECORD_ID1, RECORD_ID2));
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getRecordIds());
        assertThat(result.getRecordCount(), is(0));
        verify(logger).error(eq("Json processing exception when updating record: " + RECORD_ID1), any(JsonProcessingException.class));
        verify(logger).error(eq("Json processing exception when updating record: " + RECORD_ID2), any(JsonProcessingException.class));
        verify(ingestionService, never()).createUpdateRecords(eq(false), any(), eq(USER), eq(COLLABORATION_CONTEXT));
    }

    @Test
    @Ignore("Need to fix PatchRecordsResponse.recordIds for data update")
    public void shouldPatchPartiallyRecordDataPatch_ifOneRecordIsNotFound() throws IOException {
        JsonPatch inValidJsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\":\"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfoWithInvalidRecords();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(RECORD_IDS, inValidJsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(inValidJsonPatch);
        assertThat(result.getNotFoundRecordIds(), contains(RECORD_ID2));
        assertThat(result.getRecordIds(), contains(RECORD_ID1));
        assertThat(result.getRecordCount(), is(1));
        assertThat(result.getNotFoundRecordIds().size(), is(1));
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        Record patchedRecord1 = getRecord(RECORD_ID1);
        patchedRecord1.setData(Collections.singletonMap("Hello", "world"));
        verify(ingestionService).createUpdateRecords(false, Collections.singletonList(patchedRecord1), USER, COLLABORATION_CONTEXT);
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

    private MultiRecordInfo getMultiRecordInfoWithInvalidRecords() {
        MultiRecordInfo multiRecordInfo = new MultiRecordInfo();
        Record record1 = getRecord(RECORD_ID1);

        multiRecordInfo.setRecords(Collections.singletonList(record1));
        multiRecordInfo.setInvalidRecords(Collections.singletonList(RECORD_ID2));
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
