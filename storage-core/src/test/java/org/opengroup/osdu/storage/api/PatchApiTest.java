package org.opengroup.osdu.storage.api;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.core.common.model.storage.RecordBulkUpdateParam;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.response.BulkUpdateRecordsResponse;
import org.opengroup.osdu.storage.service.RecordService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.inject.Provider;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class PatchApiTest {
    private final String USER = "user";
    private final String TENANT = "tenant1";

    @Mock
    private Provider<RecordService> recordServiceProvider;

    @Mock
    private Provider<DpsHeaders> headersProvider;

    @Mock
    private RecordService recordService;

    @Mock
    private DpsHeaders httpHeaders;

    @InjectMocks
    private PatchApi sut;

    @Before
    public void setup() {
        initMocks(this);

        when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);
        when(this.httpHeaders.getPartitionIdWithFallbackToAccountId()).thenReturn(this.TENANT);

        when(this.headersProvider.get()).thenReturn(this.httpHeaders);
        when(this.recordServiceProvider.get()).thenReturn(this.recordService);

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

        when(this.recordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER)).thenReturn(expectedResponse);

        ResponseEntity<BulkUpdateRecordsResponse> response = this.sut.updateRecordsMetadata(recordBulkUpdateParam);

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

        when(this.recordService.bulkUpdateRecords(recordBulkUpdateParam, this.USER)).thenReturn(expectedResponse);

        ResponseEntity<BulkUpdateRecordsResponse> response = this.sut.updateRecordsMetadata(recordBulkUpdateParam);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());
    }
}