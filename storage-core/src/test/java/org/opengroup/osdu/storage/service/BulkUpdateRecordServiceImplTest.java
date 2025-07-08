// Copyright 2017-2021, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.util.api.RecordUtil;
import org.opengroup.osdu.storage.validation.api.PatchOperationValidator;

import java.time.Clock;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

@ExtendWith(MockitoExtension.class)
public class BulkUpdateRecordServiceImplTest {

    private static final String PATCH_OPERATION = "replace";

    private static final String PATH = "/path";

    private static final String[] VALUES = {"value"};

    private final static Long CURRENT_MILLIS = System.currentTimeMillis();

    private static final String TEST_USER = "testUser";

    private static final String TEST_ID = "test_id";
    private static final String ACL_OWNER = "test_acl";
    private static final String ACL_NON_OWNER = "test_acl_non_owner";
    private static final String TEST_KIND = "test_kind";

    private static final String ID = "test_id";

    private final static List<String> TEST_IDS = singletonList(TEST_ID);
    private final static Map<String, String> IDS_VERSION_MAP = new HashMap<String, String>() {{
        put(TEST_ID, TEST_ID);
    }};
    private final static String[] OWNERS = new String[]{ACL_OWNER};
    private final static String[] NON_OWNERS = new String[]{ACL_NON_OWNER};
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    //External dependencies
    @Mock
    private RecordUtil recordUtil;
    @Mock
    private IRecordsMetadataRepository recordRepository;
    @Mock
    private PatchOperationValidator patchOperationValidator;
    @Mock
    private DpsHeaders headers;
    @Mock
    private IEntitlementsExtensionService entitlementsAndCacheService;
    @Mock
    private StorageAuditLogger auditLogger;
    @Mock
    private PersistenceService persistenceService;
    @Mock
    private Clock clock;
    @Mock
    private DataAuthorizationService dataAuthorizationService;
    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private IOPAService opaService;
    @InjectMocks
    private BulkUpdateRecordServiceImpl service;

    @Mock
    private IFeatureFlag featureFlag;

    @Test
    public void should_bulkUpdateRecords_successfully() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        commonSetup(recordMetadataMap, param.getOps(), true, false);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        commonVerify(singletonList(TEST_ID), param.getOps());
        verify(persistenceService, only()).updateMetadata(singletonList(recordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, only()).createOrUpdateRecordsSuccess(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
    }

    @Test
    public void should_bulkUpdateRecords_successfully_whenDataManager() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        commonSetup(recordMetadataMap, param.getOps(), true, false);
        when(this.entitlementsAndCacheService.isDataManager(any())).thenReturn(true);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        commonVerify(singletonList(TEST_ID), param.getOps());
        verify(persistenceService, only()).updateMetadata(singletonList(recordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, only()).createOrUpdateRecordsSuccess(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
    }

    @Test
    public void should_bulkUpdateRecords_successfully_when_recordMetadataNotFound() {
        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();

        commonSetup(new HashMap<>(), param.getOps(), false, false);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertEquals(TEST_ID, actualResponse.getNotFoundRecordIds().get(0));
    }

    @Test
    public void should_bulkUpdateRecords_successfully_when_recordUserDonHaveOwnerAccess() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        commonSetup(recordMetadataMap, param.getOps(), false, false);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertEquals(TEST_ID, actualResponse.getUnAuthorizedRecordIds().get(0));
    }

    @Test
    public void should_bulkUpdateRecords_successfully_when_recordIsLocked() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        commonSetup(recordMetadataMap, param.getOps(), true, true);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(persistenceService, only()).updateMetadata(singletonList(recordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getLockedRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
    }

    @Test
    public void should_bulkUpdateRecordsWithOpaEnabled_successfully() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(recordMetadataMap);
        when(persistenceService.updateMetadata(singletonList(recordMetadataMap.get(TEST_ID)), TEST_IDS, IDS_VERSION_MAP, Optional.empty()))
                .thenReturn(emptyList());
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(recordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(recordMetadataMap.get(TEST_ID));

        List<OpaError> errors = new ArrayList<>();
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        commonVerify(singletonList(TEST_ID), param.getOps());
        verify(persistenceService, only()).updateMetadata(singletonList(recordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, only()).createOrUpdateRecordsSuccess(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
    }

    @Test
    public void should_bulkUpdateRecordsWithOpaEnabled_successfully_when_recordMetadataNotFound() {
        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();

        Map<String, RecordMetadata> recordMetadataMap = new HashMap<>();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(clock.millis()).thenReturn(CURRENT_MILLIS);

        List<OpaError> errors = new ArrayList<>();
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, COLLABORATION_CONTEXT);

        verifyNoMoreInteractions(entitlementsAndCacheService, headers, persistenceService);
        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertEquals(TEST_ID, actualResponse.getNotFoundRecordIds().get(0));
    }

    @Test
    public void should_bulkUpdateRecordsWithOpaEnabled_successfully_when_recordUserDonHaveOwnerAccess() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(recordMetadataMap);
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(recordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(recordMetadataMap.get(TEST_ID));
        //when(CollaborationUtilImpl.getIdWithNamespace(ID, COLLABORATION_CONTEXT)).thenReturn(ID);

        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("You must be an owner to update a record").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertEquals(TEST_ID, actualResponse.getUnAuthorizedRecordIds().get(0));
    }

    @Test
    public void should_bulkUpdateRecordsWithOpaEnabled_successfully_when_recordIsLocked() {
        RecordMetadata recordMetadata = buildRecordMetadata();
        Map<String, RecordMetadata> recordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, recordMetadata);
        }};

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(recordMetadataMap);
        when(persistenceService.updateMetadata(singletonList(recordMetadataMap.get(TEST_ID)), TEST_IDS, IDS_VERSION_MAP, Optional.empty()))
                .thenReturn(new ArrayList<>(singletonList(TEST_ID)));
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(recordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(recordMetadataMap.get(TEST_ID));
        //when(CollaborationUtilImpl.getIdWithNamespace(ID, COLLABORATION_CONTEXT)).thenReturn(ID);

        List<OpaError> errors = new ArrayList<>();
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
        List<ValidationOutputRecord> validationOutputRecords = new ArrayList<>();
        validationOutputRecords.add(validationOutputRecord1);
        when(this.opaService.validateUserAccessToRecords(any(), any())).thenReturn(validationOutputRecords);


        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(persistenceService, only()).updateMetadata(singletonList(recordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, only()).createOrUpdateRecordsFail(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getLockedRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
    }
    
    @Test
    public void should_fail_when_patchingExistingRecordThatFailDataAuthorizationCheck_IntegrateOPA() {
    	RecordMetadata existingRecordMetadata = buildRecordMetadata();
    	Acl non_owner_acl = new Acl();
    	non_owner_acl.setOwners(NON_OWNERS);
    	existingRecordMetadata.setAcl(non_owner_acl);
        Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, existingRecordMetadata);
        }};
        
        RecordMetadata newRecordMetadata = buildRecordMetadata();

        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(existingRecordMetadataMap);
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(existingRecordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(newRecordMetadata);
        
        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
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

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(persistenceService, times(0)).updateMetadata(any(), any(), any(), any());
        verify(auditLogger, times(1)).createOrUpdateRecordsFail(TEST_IDS);
        verify(auditLogger, times(0)).createOrUpdateRecordsSuccess(TEST_IDS);
        
        assertEquals(TEST_ID, actualResponse.getUnAuthorizedRecordIds().get(0));
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getRecordIds().isEmpty());
    }
    
    @Test
    public void should_fail_when_patchingWithNewRecordThatFailDataAuthorizationCheck_IntegrateOPA() {
    	RecordMetadata existingRecordMetadata = buildRecordMetadata();
    	Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, existingRecordMetadata);
        }};
    	
    	RecordMetadata newRecordMetadata = buildRecordMetadata();
    	Acl non_owner_acl = new Acl();
    	non_owner_acl.setOwners(NON_OWNERS);
    	newRecordMetadata.setAcl(non_owner_acl);
        
        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(existingRecordMetadataMap);
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(existingRecordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(newRecordMetadata);
        
        List<OpaError> errors = new ArrayList<>();
        errors.add(OpaError.builder().message("The user is not authorized to perform this action").reason("Access denied").code("403").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(TEST_ID).errors(errors).build();
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

        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());

        verify(persistenceService, times(0)).updateMetadata(any(), any(), any(), any());
        verify(auditLogger, times(1)).createOrUpdateRecordsFail(TEST_IDS);
        verify(auditLogger, times(0)).createOrUpdateRecordsSuccess(TEST_IDS);
        
        assertEquals(TEST_ID, actualResponse.getUnAuthorizedRecordIds().get(0));
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getRecordIds().isEmpty());
    }
    
    @Test
    public void should_success_when_patchingRecordThatPassDataAuthorizationCheck_IntegrateOPA() {
    	RecordMetadata existingRecordMetadata = buildRecordMetadata();
    	Map<String, RecordMetadata> existingRecordMetadataMap = new HashMap<String, RecordMetadata>() {{
            put(TEST_ID, existingRecordMetadata);
        }};
    	
    	RecordMetadata newRecordMetadata = buildRecordMetadata();
        
        RecordBulkUpdateParam param = buildRecordBulkUpdateParam();
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(existingRecordMetadataMap);
        when(persistenceService.updateMetadata(singletonList(newRecordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty()))
        .thenReturn(emptyList());
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        when(recordUtil.updateRecordMetaDataForPatchOperations(existingRecordMetadataMap.get(TEST_ID), param.getOps(), TEST_USER,
                CURRENT_MILLIS)).thenReturn(newRecordMetadata);
        
        BulkUpdateRecordsResponse actualResponse = service.bulkUpdateRecords(param, TEST_USER, Optional.empty());
        
        commonVerify(singletonList(TEST_ID), param.getOps());
        verify(persistenceService, times(1)).updateMetadata(singletonList(newRecordMetadata), TEST_IDS, IDS_VERSION_MAP, Optional.empty());
        verify(auditLogger, times(1)).createOrUpdateRecordsSuccess(TEST_IDS);
        verify(auditLogger, times(0)).createOrUpdateRecordsFail(TEST_IDS);

        assertEquals(TEST_ID, actualResponse.getRecordIds().get(0));
        assertTrue(actualResponse.getNotFoundRecordIds().isEmpty());
        assertTrue(actualResponse.getUnAuthorizedRecordIds().isEmpty());
        assertTrue(actualResponse.getLockedRecordIds().isEmpty());
    }

    private static RecordMetadata buildRecordMetadata() {
        Acl acl = new Acl();
        acl.setOwners(OWNERS);
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(TEST_ID);
        recordMetadata.setKind(TEST_KIND);
        recordMetadata.setAcl(acl);
        return recordMetadata;
    }

    private RecordBulkUpdateParam buildRecordBulkUpdateParam() {
        RecordQuery query = new RecordQuery();
        query.setIds(new ArrayList<>(singletonList(ID)));
        List<PatchOperation> ops = new ArrayList<>();
        PatchOperation op = PatchOperation.builder().op(PATCH_OPERATION).path(PATH).value(VALUES).build();
        ops.add(op);
        return RecordBulkUpdateParam.builder().query(query).ops(ops).build();
    }

    private void commonSetup(Map<String, RecordMetadata> recordMetadataMap,
                             List<PatchOperation> patchOperations,
                             boolean hasOwnerAccess,
                             boolean isLockedRecord) {
        when(recordRepository.get(TEST_IDS, Optional.empty())).thenReturn(recordMetadataMap);
        lenient().when(persistenceService.updateMetadata(singletonList(recordMetadataMap.get(TEST_ID)), TEST_IDS, IDS_VERSION_MAP, Optional.empty()))
                .thenReturn(isLockedRecord ? new ArrayList<>(singletonList(TEST_ID)) : emptyList());
        when(clock.millis()).thenReturn(CURRENT_MILLIS);
        lenient().when(entitlementsAndCacheService.hasOwnerAccess(headers, OWNERS)).thenReturn(hasOwnerAccess);
        lenient().when(recordUtil.updateRecordMetaDataForPatchOperations(recordMetadataMap.get(TEST_ID), patchOperations, TEST_USER,
                CURRENT_MILLIS)).thenReturn(recordMetadataMap.get(TEST_ID));
    }

    private void commonVerify(List<String> ids, List<PatchOperation> ops) {
        recordUtil.validateRecordIds(ids);
        this.patchOperationValidator.validateDuplicates(ops);
        this.patchOperationValidator.validateAcls(ops);
        this.patchOperationValidator.validateLegalTags(ops);
        this.patchOperationValidator.validateTags(ops);
    }
}
