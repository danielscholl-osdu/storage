package org.opengroup.osdu.storage.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.BulkUpdateRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private PatchApi sut;

    @BeforeEach
    public void setup() {
        initMocks(this);

        lenient().when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);

        lenient().when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());

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
        BulkUpdateRecordsResponse expectedResponse = BulkUpdateRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, Optional.empty())).thenReturn(expectedResponse);

        ResponseEntity<BulkUpdateRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

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
        BulkUpdateRecordsResponse expectedResponse = BulkUpdateRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, Optional.empty())).thenReturn(expectedResponse);

        ResponseEntity<BulkUpdateRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

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
        BulkUpdateRecordsResponse expectedResponse = BulkUpdateRecordsResponse.builder()
                .recordCount(6)
                .recordIds(validRecordIds)
                .notFoundRecordIds(notFoundRecordIds)
                .unAuthorizedRecordIds(unAuthorizedRecordIds)
                .lockedRecordIds(lockedRecordIds)
                .build();

        when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);

        when(this.bulkUpdateRecordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER, COLLABORATION_CONTEXT)).thenReturn(expectedResponse);

        ResponseEntity<BulkUpdateRecordsResponse> response = this.sut.updateRecordsMetadata(COLLABORATION_DIRECTIVES, recordBulkUpdateParam);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }

}
