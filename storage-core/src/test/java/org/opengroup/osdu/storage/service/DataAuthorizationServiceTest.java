// Copyright 2017-2023, Schlumberger
// Copyright © Microsoft Corporation
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opengroup.osdu.storage.util.RecordConstants.OPA_FEATURE_NAME;

@ExtendWith(MockitoExtension.class)
public class DataAuthorizationServiceTest {

    private static final String HEADER_ACCOUNT_ID = "anyTenant";
    private static final String HEADER_AUTHORIZATION = "anyCrazyToken";

    @Mock
    private DpsHeaders headers;
    @Mock
    private IPolicyService policyService;
    @Mock
    private PartitionPolicyStatusService statusService;
    @Mock
    private IEntitlementsExtensionService entitlementsService;
    @Mock
    private ICloudStorage cloudStorage;
    @Mock
    private IOPAService opaService;
    @Mock
    private JaxRsDpsLog logger;
    @InjectMocks
    private DataAuthorizationService sut;

    @Mock
    private IFeatureFlag featureFlag;

    private static final Map<String, String> headerMap = new HashMap<>();

    @BeforeEach
    public void setup() {
        setDefaultHeaders();
        this.headers = DpsHeaders.createFromMap(headerMap);
    }

    private void setDefaultHeaders() {
        headerMap.put(DpsHeaders.ACCOUNT_ID, HEADER_ACCOUNT_ID);
        headerMap.put(DpsHeaders.AUTHORIZATION, HEADER_AUTHORIZATION);
    }

    @Test
    public void should_callOpaServiceInOwnerAccessValidation_when_opaIsEnabled() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(1)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callOpaServiceInViewerOrOwnerAccessValidation_when_OpaIsEnabled() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(1)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callEntitlementService_when_policyServiceDisabled() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(false);
        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(1)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callEntitlementServiceInViewerOrOwnerAccessValidation_when_opaIsDisabled() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(false);
        this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(1)).hasValidAccess(any(), any());
    }

    @Test
    public void should_returnTrue_validateOwnerAccess_when_dataManager() {
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_returnTrue_validateViewerOrOwnerAccess_when_dataManager() {
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_returnTrue_hasAccess_when_dataManager_False() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.entitlementsService.isDataManager(any())).thenReturn(false);
        assertTrue(this.sut.hasAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(1)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(1)).isDataManager(any());
    }

    @Test
    public void should_returnTrue_hasAccess_when_dataManager() {
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.hasAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_returnFalse_hasAccess_when_opaEnabled_andRecordInactive() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.entitlementsService.isDataManager(any())).thenReturn(false);

        RecordMetadata metadata = getRecordMetadata();
        metadata.setStatus(RecordState.deleted);

        assertFalse(this.sut.hasAccess(metadata, OperationType.view));
        verify(this.opaService, never()).validateUserAccessToRecords(any(), any());
    }

    @Test
    public void should_returnFalse_hasAccess_when_opaEnabled_andRecordHasNoVersions() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(true);
        when(this.entitlementsService.isDataManager(any())).thenReturn(false);

        RecordMetadata metadata = getRecordMetadata();
        metadata.setGcsVersionPaths(Collections.emptyList());

        assertFalse(this.sut.hasAccess(metadata, OperationType.view));
        verify(this.opaService, never()).validateUserAccessToRecords(any(), any());
    }

    @Test
    public void should_callCloudStorage_hasAccess_when_opaDisabled() {
        when(featureFlag.isFeatureEnabled(OPA_FEATURE_NAME)).thenReturn(false);
        when(this.entitlementsService.isDataManager(any())).thenReturn(false);
        when(this.cloudStorage.hasAccess(any(RecordMetadata.class))).thenReturn(true);

        assertTrue(this.sut.hasAccess(this.getRecordMetadata(), OperationType.view));
        verify(this.cloudStorage, times(1)).hasAccess(any(RecordMetadata.class));
    }

    @Test
    public void should_returnTrue_policyEnabled_when_policyServiceAndStatusEnabled() {
        when(this.statusService.policyEnabled(any())).thenReturn(true);

        assertTrue(this.sut.policyEnabled());
    }

    @Test
    public void should_returnFalse_policyEnabled_when_policyServiceIsNull() {
        ReflectionTestUtils.setField(sut, "policyService", null);

        assertFalse(this.sut.policyEnabled());
    }

    @Test
    public void should_returnFalse_doesUserHasAccessToData_when_opaReturnsErrors() {
        OpaError opaError = OpaError.builder().message("Access denied by policy").build();
        ValidationOutputRecord errorRecord = ValidationOutputRecord.builder()
                .id("id:access")
                .errors(Collections.singletonList(opaError))
                .build();
        when(this.opaService.validateUserAccessToRecords(any(), any()))
                .thenReturn(Collections.singletonList(errorRecord));

        assertFalse(this.sut.doesUserHasAccessToData(
                Collections.singletonList(getRecordMetadata()), OperationType.view));
        verify(this.logger).error(contains("Data authorization failure"));
    }

    @Test
    public void should_returnTrue_doesUserHasAccessToData_when_opaReturnsNoErrors() {
        ValidationOutputRecord successRecord = ValidationOutputRecord.builder()
                .id("id:access")
                .errors(Collections.emptyList())
                .build();
        when(this.opaService.validateUserAccessToRecords(any(), any()))
                .thenReturn(Collections.singletonList(successRecord));

        assertTrue(this.sut.doesUserHasAccessToData(
                Collections.singletonList(getRecordMetadata()), OperationType.view));
    }

    private RecordMetadata getRecordMetadata() {
        Acl acl = new Acl();
        String[] viewers = new String[]{"viewer1@devint.osdu.com", "viewer2@devint.osdu.com"};
        String[] owners = new String[]{"owner1@devint.osdu.com", "owner2@devint.osdu.com"};
        acl.setViewers(viewers);
        acl.setOwners(owners);

        RecordMetadata record = new RecordMetadata();
        record.setAcl(acl);
        record.setKind("any kind");
        record.setId("id:access");
        record.setStatus(RecordState.active);
        record.setGcsVersionPaths(Arrays.asList("path/1", "path/2", "path/3"));

        return record;
    }
}
