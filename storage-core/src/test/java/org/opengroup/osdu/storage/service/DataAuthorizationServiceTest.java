package org.opengroup.osdu.storage.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.policy.PolicyResponse;
import org.opengroup.osdu.core.common.model.policy.Result;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.policy.service.IPolicyService;
import org.opengroup.osdu.storage.policy.service.PartitionPolicyStatusService;
import org.opengroup.osdu.storage.provider.interfaces.ICloudStorage;

import java.util.*;

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
    public void should_callPolicyService_when_policyServiceEnabled() {
        when(this.statusService.policyEnabled(this.headers.getPartitionId())).thenReturn(true);

        Result result = new Result();
        result.setAllow(true);
        PolicyResponse response = new PolicyResponse();
        response.setResult(result);
        when(this.policyService.evaluatePolicy(any())).thenReturn(response);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.owner1@devint.osdu.com");
        groupInfo.setEmail("data.owner1@devint.osdu.com");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        when(this.entitlementsService.getGroups(any())).thenReturn(groups);

        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.entitlementsService, times(0)).hasOwnerAccess(any(), any());
    }

    @Test
    public void should_callEntitlementService_when_policyServiceDisabled() {
        when(this.statusService.policyEnabled(this.headers.getPartitionId())).thenReturn(false);

        this.sut.validateOwnerAccess(this.getRecordMetadata(), OperationType.update);

        verify(this.entitlementsService, times(1)).hasOwnerAccess(any(), any());
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
