package org.opengroup.osdu.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.gson.Gson;

import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchInputValidator;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static java.util.Collections.singletonList;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatchRecordsServiceImplTest {

    private static final String RECORD_ID1 = "tenant:record:Id1";
    private static final String RECORD_ID2 = "tenant:record:Id2";
    private static final long RECORD_VERSION = 123L;
    private static final String[] OWNERS = new String[]{"owner1@company.com", "owner2@company.com"};
    private static final String[] NON_OWNERS = new String[]{"not_owner@company.com"};
    private static final String[] VIEWERS = new String[]{"viewer1@company.com", "viewer2@company.com"};
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
    private final List<String> recordIds = new LinkedList<>(Arrays.asList(RECORD_ID1, RECORD_ID2));

    @Test
    public void shouldSuccessfullyPatchRecordData() throws IOException {
        Record patchedRecord1 = getRecord(RECORD_ID1);
        patchedRecord1.setData(Collections.singletonMap("Hello", "world"));
        Record patchedRecord2 = getRecord(RECORD_ID2);
        patchedRecord2.setData(Collections.singletonMap("Hello", "world"));
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);
        TransferInfo transferInfo = new TransferInfo();
        transferInfo.setVersion(1L);
        when(ingestionService.createUpdateRecords(false, Arrays.asList(patchedRecord1, patchedRecord2), USER, COLLABORATION_CONTEXT)).thenReturn(transferInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getErrors());
        List<String> responseRecordIds = new LinkedList<>(Arrays.asList(RECORD_ID1+":1", RECORD_ID2+":1"));
        assertEquals(responseRecordIds, result.getRecordIds());
        assertThat(result.getRecordCount(), is(2));
        verify(ingestionService).createUpdateRecords(false, Arrays.asList(patchedRecord1, patchedRecord2), USER, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldSuccessfullyPatchRecordData_withMultipleOpsOnTheSamePath() throws IOException {
        Record patchedRecord1 = getRecord(RECORD_ID1);
        patchedRecord1.setData(Collections.singletonMap("Hello again", "world"));
        Record patchedRecord2 = getRecord(RECORD_ID2);
        patchedRecord2.setData(Collections.singletonMap("Hello again", "world"));
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello again\" : \"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);
        TransferInfo transferInfo = new TransferInfo();
        transferInfo.setVersion(1L);
        when(ingestionService.createUpdateRecords(false, Arrays.asList(patchedRecord1, patchedRecord2), USER, COLLABORATION_CONTEXT)).thenReturn(transferInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getErrors());
        List<String> responseRecordIds = new LinkedList<>(Arrays.asList(RECORD_ID1+":1", RECORD_ID2+":1"));
        assertEquals(responseRecordIds, result.getRecordIds());
        assertThat(result.getRecordCount(), is(2));
        verify(ingestionService).createUpdateRecords(false, Arrays.asList(patchedRecord1, patchedRecord2), USER, COLLABORATION_CONTEXT);
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldSuccessfullyPatchRecordMetaData_whenOpaIsEnabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        List<RecordMetadata> recordMetadataList = new ArrayList<>(existingRecords.values());
        when(opaService.validateUserAccessToRecords(recordMetadataList, OperationType.update)).thenReturn(Collections.emptyList());
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(existingRecords.get(RECORD_ID1), jsonPatch);
        jsonPatchPerRecord.put(existingRecords.get(RECORD_ID2), jsonPatch);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getErrors());
        List<String> responseRecordIds = new LinkedList<>(Arrays.asList(RECORD_ID1+":"+RECORD_VERSION, RECORD_ID2+":"+RECORD_VERSION));
        assertEquals(responseRecordIds, result.getRecordIds());
        assertThat(result.getRecordCount(), is(2));
        verify(persistenceService).patchRecordsMetadata(anyMap(), eq(COLLABORATION_CONTEXT));
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());

    }

    @Test
    public void shouldSuccessfullyPatchRecordMetaData_whenOpaIsDisabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        when(entitlementsAndCacheService.hasOwnerAccess(headers, OWNERS)).thenReturn(true);
        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(existingRecords.get(RECORD_ID1), jsonPatch);
        jsonPatchPerRecord.put(existingRecords.get(RECORD_ID2), jsonPatch);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        verify(patchInputValidator).validateLegalTags(jsonPatch);
        verify(entitlementsAndCacheService, times(2)).hasOwnerAccess(headers, OWNERS);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getErrors());
        List<String> responseRecordIds = new LinkedList<>(Arrays.asList(RECORD_ID1+":"+RECORD_VERSION, RECORD_ID2+":"+RECORD_VERSION));
        assertEquals(responseRecordIds, result.getRecordIds());
        assertThat(result.getRecordCount(), is(2));
        verify(persistenceService).patchRecordsMetadata(anyMap(), eq(COLLABORATION_CONTEXT));
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldPatchRecordDataPartially_ifResultHasEmptyAclForOneRecord() throws IOException {
        Record patchedRecord2 = getRecord(RECORD_ID2);

        String[] record2ViewersAcl = new String[]{"viewer2@company.com"};
        patchedRecord2.getAcl().setViewers(record2ViewersAcl);
        patchedRecord2.setData(Collections.singletonMap("Hello", "world"));

        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"remove\", \"path\":\"/acl/viewers/0\"}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));

        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        String[] record1ViewersAcl = new String[]{"viewer1@company.com"};
        multiRecordInfo.getRecords().get(0).getAcl().setViewers(record1ViewersAcl);
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);
        TransferInfo transferInfo = new TransferInfo();
        transferInfo.setVersion(1L);
        when(ingestionService.createUpdateRecords(false, Arrays.asList(patchedRecord2), USER, COLLABORATION_CONTEXT)).thenReturn(transferInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(1, result.getFailedRecordIds().size());
        assertEquals(RECORD_ID1, result.getFailedRecordIds().get(0));
        assertEquals(1, result.getErrors().size());
        assertEquals("Patch operation for record: " + RECORD_ID1 + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers", result.getErrors().get(0));
        assertEquals(RECORD_ID2+":1", result.getRecordIds().get(0));
        assertThat(result.getRecordCount(), is(1));

        verify(ingestionService).createUpdateRecords(false, Arrays.asList(patchedRecord2), USER, COLLABORATION_CONTEXT);

        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldNotPatchData_ifResultHasEmptyLegaltagsForAllRecords() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"remove\", \"path\":\"/legal/legaltags/0\"}, {\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\" : \"world\"}}]"));

        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertEquals(2, result.getFailedRecordIds().size());
        assertEquals(RECORD_ID1, result.getFailedRecordIds().get(0));
        assertEquals(RECORD_ID2, result.getFailedRecordIds().get(1));
        assertEquals(2, result.getErrors().size());
        assertEquals("Patch operation for record: " + RECORD_ID1 + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers", result.getErrors().get(0));
        assertEquals("Patch operation for record: " + RECORD_ID2 + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers", result.getErrors().get(1));
        assertTrue(result.getRecordIds().isEmpty());

        verify(ingestionService, never()).createUpdateRecords(eq(false), anyList(), eq(USER), eq(COLLABORATION_CONTEXT));

        verify(auditLogger, never()).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldPatchRecordMetadataPartially_ifResultHasEmptyAclForOneRecord() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"remove\", \"path\":\"/acl/viewers/0\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();
        String[] record1ViewersAcl = new String[]{"viewer1@company.com"};
        existingRecords.get(RECORD_ID1).setAcl(new Acl(record1ViewersAcl, OWNERS));

        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        when(entitlementsAndCacheService.hasOwnerAccess(headers, OWNERS)).thenReturn(true);

        Map<RecordMetadata, JsonPatch> jsonPatchPerRecord = new HashMap<>();
        jsonPatchPerRecord.put(existingRecords.get(RECORD_ID2), jsonPatch);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        verify(patchInputValidator).validateLegalTags(jsonPatch);
        verify(entitlementsAndCacheService, times(2)).hasOwnerAccess(headers, OWNERS);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(1, result.getFailedRecordIds().size());
        assertEquals(RECORD_ID1, result.getFailedRecordIds().get(0));
        assertEquals(1, result.getErrors().size());
        assertEquals("Patch operation for record: " + RECORD_ID1 + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers", result.getErrors().get(0));
        assertEquals(RECORD_ID2+":"+RECORD_VERSION, result.getRecordIds().get(0));
        assertThat(result.getRecordCount(), is(1));
        verify(persistenceService).patchRecordsMetadata(anyMap(), eq(COLLABORATION_CONTEXT));
        verify(auditLogger).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldNotPatchMetadata_ifResultHasEmptyLegaltagsForAllRecords() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"remove\", \"path\":\"/legal/legaltags/0\"}]"));
        Map<String, RecordMetadata> existingRecords = getExistingRecordsMetadata();

        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecords);
        when(entitlementsAndCacheService.hasOwnerAccess(headers, OWNERS)).thenReturn(true);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        verify(patchInputValidator).validateLegalTags(jsonPatch);
        verify(entitlementsAndCacheService, times(2)).hasOwnerAccess(headers, OWNERS);
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(2, result.getFailedRecordIds().size());
        assertTrue(result.getFailedRecordIds().contains(RECORD_ID1));
        assertTrue(result.getFailedRecordIds().contains(RECORD_ID2));
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().contains("Patch operation for record: " + RECORD_ID1 + " aborted. Potentially empty value of legaltags or acl/owners or acl/viewers"));
        assertTrue(result.getRecordIds().isEmpty());
        assertThat(result.getRecordCount(), is(0));
        verify(persistenceService, never()).patchRecordsMetadata(anyMap(), eq(COLLABORATION_CONTEXT));
        verify(auditLogger, never()).createOrUpdateRecordsSuccess(result.getRecordIds());
    }

    @Test
    public void shouldFailRecordDataPatch_ifValueInvalid() throws IOException {
        JsonPatch inValidJsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":\"inValidDataFormat\"}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfo();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, inValidJsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(inValidJsonPatch);
        assertThat(result.getFailedRecordIds(), containsInAnyOrder(RECORD_ID1, RECORD_ID2));
        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getRecordIds());
        assertThat(result.getRecordCount(), is(0));
        assertThat(result.getFailedRecordIds().size(), is(2));
        assertThat(result.getErrors().size(), is(2));
        assertThat(result.getErrors(), contains("Json patch error for record: " + RECORD_ID1, "Json patch error for record: " + RECORD_ID2));
        verify(logger).error(eq("Json patch error for record: " + RECORD_ID1 + "|Json patch error for record: " + RECORD_ID2));
        verify(ingestionService, never()).createUpdateRecords(eq(false), any(), eq(USER), eq(COLLABORATION_CONTEXT));
    }

    @Test
    public void shouldPatchPartiallyRecordDataPatch_ifOneRecordIsNotFound() throws IOException {
        Record patchedRecord1 = getRecord(RECORD_ID1);
        patchedRecord1.setData(Collections.singletonMap("Hello", "world"));
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/data\", \"value\":{\"Hello\":\"world\"}}]"));
        MultiRecordInfo multiRecordInfo = getMultiRecordInfoWithInvalidRecords();
        when(batchService.getMultipleRecords(any(MultiRecordIds.class), eq(COLLABORATION_CONTEXT))).thenReturn(multiRecordInfo);
        TransferInfo transferInfo = new TransferInfo();
        transferInfo.setVersion(1L);
        when(ingestionService.createUpdateRecords(false, Arrays.asList(patchedRecord1), USER, COLLABORATION_CONTEXT)).thenReturn(transferInfo);

        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        verifyValidatorsWereInvocated(jsonPatch);
        assertThat(result.getNotFoundRecordIds(), contains(RECORD_ID2));
        assertThat(result.getRecordIds(), contains(RECORD_ID1+":1"));
        assertThat(result.getRecordCount(), is(1));
        assertThat(result.getNotFoundRecordIds().size(), is(1));
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        verify(ingestionService).createUpdateRecords(false, Collections.singletonList(patchedRecord1), USER, COLLABORATION_CONTEXT);
    }
    
    @Test
    public void shouldFailPatchExistingRecordMetaDataThatFailDataAuthorizationCheck_whenOpaIsEnabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        List<String> recordIds = singletonList(RECORD_ID1);
        
        RecordMetadata existingRecordMetadata = getRecordMetadata(RECORD_ID1);
        existingRecordMetadata.setAcl(new Acl(VIEWERS, NON_OWNERS));
        Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID1, existingRecordMetadata);
        }};
        
        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"add\", \"path\":\"/acl/viewers/-\", \"value\":\"value\"}]"));
        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecordMetadataMap);
        
        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(
        		argThat( (List<RecordMetadata> recordsMetadata) -> {
        			for (RecordMetadata recordMetadata : recordsMetadata) {
        				if (Arrays.equals(recordMetadata.getAcl().owners, NON_OWNERS)) 
        					return true;
        				} 
        			return false;
        			} 
        		), 
        		argThat( (OperationType operationType) -> operationType == OperationType.update))).thenReturn(validationOutputRecords);
        
        try {
        	sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        }
    }
    
    @Test
    public void shouldFailPatchWithNewRecordMetaDataThatFailDataAuthorizationCheck_whenOpaIsEnabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        List<String> recordIds = singletonList(RECORD_ID1);
        
        RecordMetadata existingRecordMetadata = getRecordMetadata(RECORD_ID1);
        Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID1, existingRecordMetadata);
        }};

        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"replace\", \"path\":\"/acl/owners\", \"value\":"
        		+ new Gson().toJson(NON_OWNERS) + "}]"));
        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecordMetadataMap);
        
        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(
        		argThat( (List<RecordMetadata> recordsMetadata) -> {
        			for (RecordMetadata recordMetadata : recordsMetadata) {
        				if (Arrays.equals(recordMetadata.getAcl().owners, NON_OWNERS)) 
        					return true;
        				} 
        			return false;
        			} 
        		), 
        		argThat( (OperationType operationType) -> operationType == OperationType.update))).thenReturn(validationOutputRecords);
        
        try {
        	sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);
            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        }
    }
    
    @Test
    public void shouldSuccessPatchRecordMetaDataThatPassDataAuthorizationCheck_whenOpaIsEnabled() throws IOException {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        List<String> recordIds = singletonList(RECORD_ID1);
        
        RecordMetadata existingRecordMetadata = getRecordMetadata(RECORD_ID1);
        Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(RECORD_ID1, existingRecordMetadata);
        }};

        JsonPatch jsonPatch = JsonPatch.fromJson(mapper.readTree("[{\"op\":\"replace\", \"path\":\"/acl/owners\", \"value\":"
        		+ new Gson().toJson(OWNERS) + "}]"));
        when(recordRepository.get(recordIds, COLLABORATION_CONTEXT)).thenReturn(existingRecordMetadataMap);
        
        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(
        		argThat( (List<RecordMetadata> recordsMetadata) -> {
        			for (RecordMetadata recordMetadata : recordsMetadata) {
        				if (Arrays.equals(recordMetadata.getAcl().owners, NON_OWNERS)) 
        					return true;
        				} 
        			return false;
        			} 
        		), 
        		argThat( (OperationType operationType) -> operationType == OperationType.update))).thenReturn(validationOutputRecords);


        PatchRecordsResponse result = sut.patchRecords(recordIds, jsonPatch, USER, COLLABORATION_CONTEXT);

        assertEquals(Collections.emptyList(), result.getNotFoundRecordIds());
        assertEquals(Collections.emptyList(), result.getFailedRecordIds());
        assertEquals(Collections.emptyList(), result.getErrors());
        List<String> responseRecordIds = new LinkedList<>(Arrays.asList(RECORD_ID1+":"+RECORD_VERSION));
        assertEquals(responseRecordIds, result.getRecordIds());
        assertEquals(result.getRecordCount(), Integer.valueOf(1));
        verify(persistenceService, times(1)).patchRecordsMetadata(anyMap(), eq(COLLABORATION_CONTEXT));
        verify(auditLogger, times(1)).createOrUpdateRecordsSuccess(result.getRecordIds());
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
        Legal legal = new Legal();
        legal.setLegaltags(new HashSet<>(Arrays.asList("legaltag")));
        record.setLegal(legal);
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
        Legal legal = new Legal();
        legal.setLegaltags(new HashSet<>(Arrays.asList("legaltag")));
        recordMetadata.setLegal(legal);
        recordMetadata.setGcsVersionPaths(Arrays.asList("testKind/"+recordId+"/"+RECORD_VERSION));
        return recordMetadata;
    }

    private void verifyValidatorsWereInvocated(JsonPatch jsonPatch) {
        verify(recordUtil).validateRecordIds(recordIds);
        verify(patchInputValidator).validateDuplicates(jsonPatch);
        verify(patchInputValidator).validateAcls(jsonPatch);
        verify(patchInputValidator).validateKind(jsonPatch);
        verify(patchInputValidator).validateAncestry(jsonPatch);
    }
}
