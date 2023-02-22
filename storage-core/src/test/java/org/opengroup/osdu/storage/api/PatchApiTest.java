package org.opengroup.osdu.storage.api;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.service.BulkUpdateRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatchApiTest {
    private final String USER = "user";
    private final String TENANT = "tenant1";
    private final String COLLABORATION_DIRECTIVES = "id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=TestApp";
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    @Mock
    private Provider<BulkUpdateRecordService> bulkUpdateRecordServiceProvider;

    @Mock
    private Provider<DpsHeaders> headersProvider;

    @Mock
    private BulkUpdateRecordService bulkUpdateRecordService;

    @Mock
    private DpsHeaders httpHeaders;
    
    @Mock
    private CollaborationContextFactory collaborationContextFactory;

    @Mock
    protected IEntitlementsFactory iEntitlementsFactory;

    @InjectMocks
    private PatchApi sut;

    @Before
    public void setup() {
        initMocks(this);

        when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);
        when(this.httpHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(this.TENANT);

        when(this.headersProvider.get()).thenReturn(this.httpHeaders);
        when(this.bulkUpdateRecordServiceProvider.get()).thenReturn(this.bulkUpdateRecordService);

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());

        TenantInfo tenant = new TenantInfo();
        tenant.setName(this.TENANT);
    }

    @Test
    public void should_returnsHttp206_when_bulkUpdatingRecordsPartiallySuccessfully() {
        List<String> recordIds = new ArrayList<>();
        List<String> validRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unAuthorizedRecordIds = new ArrayList<>();
        List<String> lockedRecordIds = new ArrayList<>();
        validRecordIds.add("Valid1");
        validRecordIds.add("Valid2");
        notFoundRecordIds.add("NotFound1");
        notFoundRecordIds.add("NotFound2");
        unAuthorizedRecordIds.add("UnAuthorized1");
        unAuthorizedRecordIds.add("UnAuthorized2");
        lockedRecordIds.add("lockedRecord1");
        recordIds.addAll(validRecordIds);
        recordIds.addAll(notFoundRecordIds);
        recordIds.addAll(unAuthorizedRecordIds);

        List<PatchOperation> ops = new ArrayList<>();
        ops.add(PatchOperation.builder().op("replace").path("acl/viewers").value(new String[]{"viewer@tester"}).build());

        RecordBulkUpdateParam recordBulkUpdateParam = RecordBulkUpdateParam.builder()
                .query(RecordQuery.builder().ids(recordIds).build())
                .ops(ops)
                .build();
        PatchRecordsResponse expectedResponse = PatchRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, Optional.empty())).thenReturn(expectedResponse);

        ResponseEntity<PatchRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    public void should_returnsHttp200_when_bulkUpdatingRecordsFullySuccessfully() {
        List<String> recordIds = new ArrayList<>();
        List<String> validRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unAuthorizedRecordIds = new ArrayList<>();
        List<String> lockedRecordIds = new ArrayList<>();
        validRecordIds.add("Valid1");
        validRecordIds.add("Valid2");
        recordIds.addAll(validRecordIds);

        List<PatchOperation> ops = new ArrayList<>();
        ops.add(PatchOperation.builder().op("replace").path("acl/viewers").value(new String[]{"viewer@tester"}).build());

        RecordBulkUpdateParam recordBulkUpdateParam = RecordBulkUpdateParam.builder()
                .query(RecordQuery.builder().ids(recordIds).build())
                .ops(ops)
                .build();
        PatchRecordsResponse expectedResponse = PatchRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, Optional.empty())).thenReturn(expectedResponse);

        ResponseEntity<PatchRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    public void should_returnsHttp200_when_bulkUpdatingRecordsFullySuccessfullyWithCollaborationContext() {
        List<String> recordIds = new ArrayList<>();
        List<String> validRecordIds = new ArrayList<>();
        List<String> notFoundRecordIds = new ArrayList<>();
        List<String> unAuthorizedRecordIds = new ArrayList<>();
        List<String> lockedRecordIds = new ArrayList<>();
        validRecordIds.add("Valid1");
        validRecordIds.add("Valid2");
        recordIds.addAll(validRecordIds);

        List<PatchOperation> ops = new ArrayList<>();
        ops.add(PatchOperation.builder().op("replace").path("acl/viewers").value(new String[]{"viewer@tester"}).build());

        RecordBulkUpdateParam recordBulkUpdateParam = RecordBulkUpdateParam.builder()
                .query(RecordQuery.builder().ids(recordIds).build())
                .ops(ops)
                .build();
        PatchRecordsResponse expectedResponse = PatchRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, COLLABORATION_CONTEXT)).thenReturn(expectedResponse);

        ResponseEntity<PatchRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

    @Test
    public void should_returnUnauthorized_when_patchRecordsWithViewerPermissions() throws EntitlementsException {
        //setupAuthorization(StorageRole.VIEWER);
    }

    @Test
    public void should_return400_when_patchRecordsAndOperationOtherThanAddRemoveOrReplace() {

    }

    @Test
    public void should_return400_when_patchRecordsAndUpdatingMetadataOtherThanAclTagsAncestryLegalOrKind() {

    }

    @Test
    public void should_return400_when_patchRecordsNoOperation() {

    }

    @Test
    public void should_return200_when_patchRecordsIsSuccess() {

    }

    @Test
    public void should_return206_when_patchRecordsIsPartialSuccess() {

    }

    @Test
    public void should_returnIdWithVersion_when_patchRecordsOnlyDataIsUpdated() {

    }

    @Test
    public void should_returnIdWithVersion_when_patchRecordsDataAndMetadataIsUpdated() {

    }

    @Test
    public void should_returnIdWithoutVersion_when_patchRecordsDataIsNotUpdated() {

    }

    @Test
    public void should_return200_when_patchRecordsIsSuccessWithCollaborationContext() {

    }

    protected void setupAuthorization(String role) throws EntitlementsException {
        IEntitlementsService iEntitlementsService = Mockito.mock(IEntitlementsService.class);
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName(role);
        Groups groups = new Groups();
        groups.setGroups(Collections.singletonList(groupInfo));
        groups.setMemberEmail("a@b");
        Mockito.when(iEntitlementsService.getGroups()).thenReturn(groups);
        Mockito.when(iEntitlementsFactory.create(ArgumentMatchers.any())).thenReturn(iEntitlementsService);
    }

//    private ResultActions sendPatchRequest() {
//        RecordQuery recordQuery = RecordQuery.builder().ids(Arrays.asList(new String[]{"id1"})).build();
//        PatchRecordsRequestModel requestPayload = PatchRecordsRequestModel.builder()
//                .query(recordQuery)
//                .ops(patchOps)
//                .build();
//
//        return sendRequest(requestPayload);
//    }

}
