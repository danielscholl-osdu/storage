// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditAction;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.logging.audit.AuditStatus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.StorageRole;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;


@ExtendWith(MockitoExtension.class)
public class AuditLoggerTest {

    @Mock
    private JaxRsDpsLog log;

    @InjectMocks
    private StorageAuditLogger sut;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private ReadAuditLogsConsumer readAuditLogsConsumer;


    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(dpsHeaders.getUserEmail()).thenReturn("user");
        when(dpsHeaders.getUserAuthorizedGroupName()).thenReturn("service.storage.admin");
        lenient().when(httpServletRequest.getHeader(any())).thenReturn(null);
        when(httpServletRequest.getHeader("user-agent")).thenReturn("test-agent");
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    public void should_writeCreateOrUpdateRecordsEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.createOrUpdateRecordsSuccess(resource);
        this.sut.createOrUpdateRecordsFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writeDeleteRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.deleteRecordSuccess(resource);
        this.sut.deleteRecordFail(resource);

        verify(this.log,times(2)).audit(any());
    }

    @Test
    public void should_writePurgeRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.purgeRecordSuccess(resource);
        this.sut.purgeRecordFail(resource);

        verify(this.log, times(2)).audit(any());
    }

    @Test
    public void should_writePurgeRecordVersionsSuccessEvent() {
        List<String> resource = Arrays.asList("version1", "version2");

        this.sut.purgeRecordVersionsSuccess("recordId1", resource);

        ArgumentCaptor<AuditPayload> captor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.log, times(1)).audit(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> auditLog = (Map<String, Object>) captor.getValue().get("auditLog");
        assertEquals("ST015", auditLog.get("actionId"));
        assertEquals(AuditAction.DELETE, auditLog.get("action"));
        assertEquals(AuditStatus.SUCCESS, auditLog.get("status"));
        assertEquals(String.format("Record `%s` versions purged", "recordId1"), auditLog.get("message"));
        assertEquals(resource, auditLog.get("resources"));
        assertEquals(Collections.singletonList(StorageRole.ADMIN), auditLog.get("requiredGroupsForAction"));
    }

    @Test
    public void should_writePurgeRecordVersionsFailureEvent() {
        List<String> resource = Arrays.asList("version1", "version2");

        this.sut.purgeRecordVersionsFail("recordId1", resource);

        ArgumentCaptor<AuditPayload> captor = ArgumentCaptor.forClass(AuditPayload.class);
        verify(this.log, times(1)).audit(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> auditLog = (Map<String, Object>) captor.getValue().get("auditLog");
        assertEquals("ST015", auditLog.get("actionId"));
        assertEquals(AuditAction.DELETE, auditLog.get("action"));
        assertEquals(AuditStatus.FAILURE, auditLog.get("status"));
        assertEquals(String.format("Record `%s` versions purged", "recordId1"), auditLog.get("message"));
        assertEquals(resource, auditLog.get("resources"));
        assertEquals(Collections.singletonList(StorageRole.ADMIN), auditLog.get("requiredGroupsForAction"));
    }

    @Test
    public void should_writeReadAllVersionsRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readAllVersionsOfRecordSuccess(resource);
        this.sut.readAllVersionsOfRecordFail(resource);

        verify(readAuditLogsConsumer, times(2)).accept(any());
    }

    @Test
    public void should_writeReadSpecificVersionRecordEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readSpecificVersionOfRecordSuccess(resource);
        this.sut.readSpecificVersionOfRecordFail(resource);

        verify(readAuditLogsConsumer, times(2)).accept(any());
    }

    @Test
    public void should_writeReadRecordLatestVersionEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readLatestVersionOfRecordSuccess(resource);
        this.sut.readLatestVersionOfRecordFail(resource);

        verify(readAuditLogsConsumer,times(2)).accept(any());
    }

    @Test
    public void should_writeReadMultipleRecordsEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsSuccess(resource);

        verify(readAuditLogsConsumer).accept(any());
    }

    @Test
    public void should_writeReadAllRecordsOfGivenKindEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readAllRecordsOfGivenKindSuccess(resource);

        verify(readAuditLogsConsumer).accept(any());
    }

    @Test
    public void should_writeReadAllKindsEvent() {
      List<String> resource = Collections.singletonList("1");
        this.sut.readAllKindsSuccess(resource);

        verify(readAuditLogsConsumer).accept(any());
    }

    @Test
    public void should_writeCreateSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.createSchemaSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeDeleteSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.deleteSchemaSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_writeReadSchemaEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readSchemaSuccess(resource);

        verify(readAuditLogsConsumer).accept(any());
    }

    @Test
    public void should_updateRecordComplianceStateEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.updateRecordsComplianceStateSuccess(resource);

        verify(this.log).audit(any());
    }

    @Test
    public void should_readMultipleRecordsWithOptionalConversionSuccessEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsWithOptionalConversionSuccess(resource);

        verify(readAuditLogsConsumer).accept(any());
    }

    @Test
    public void should_readMultipleRecordsWithOptionalConversionFailEvent() {
        List<String> resource = Collections.singletonList("1");
        this.sut.readMultipleRecordsWithOptionalConversionFail(resource);

        verify(readAuditLogsConsumer).accept(any());
    }
}

