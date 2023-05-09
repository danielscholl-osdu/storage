package org.opengroup.osdu.storage.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.opa.service.IOPAService;
import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
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

    private static final Map<String, String> headerMap = new HashMap<>();

    @Before
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
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(1)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callOpaServiceInViewerOrOwnerAccessValidation_when_OpaIsEnabled() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(1)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callEntitlementService_when_policyServiceDisabled() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(1)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callEntitlementServiceInViewerOrOwnerAccessValidation_when_opaIsDisabled() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(1)).hasValidAccess(any(), any());
    }

    @Test
    public void should_returnTrue_validateOwnerAccess_when_dataManager() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", false);
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_returnTrue_validateViewerOrOwnerAccess_when_dataManager() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.validateViewerOrOwnerAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_returnTrue_hasAccess_when_dataManager() {
        ReflectionTestUtils.setField(sut, "isOpaEnabled", true);
        when(this.entitlementsService.isDataManager(any())).thenReturn(true);
        assertTrue(this.sut.hasAccess(this.getRecordMetadata(), OperationType.update));

        verify(this.opaService, times(0)).validateUserAccessToRecords(any(), any());
        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
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
