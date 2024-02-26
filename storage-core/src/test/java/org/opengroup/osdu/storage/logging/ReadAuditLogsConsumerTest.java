/**
 * Copyright 2017-2019, Schlumberger
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opengroup.osdu.storage.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.partition.Property;
import org.opengroup.osdu.storage.service.IPartitionService;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
public class ReadAuditLogsConsumerTest {

    @Mock
    private JaxRsDpsLog logger;
    @Mock
    private DpsHeaders dpsHeaders;
    @Mock
    private IPartitionService partitionService;

    @InjectMocks
    private ReadAuditLogsConsumer readAuditLogsConsumer;

    private ICache<String, Boolean> readAuditLogSwitchCache;

    @BeforeEach
    public void setup() {
        readAuditLogSwitchCache = new VmCache<>(60, 2);
    }

    @Test
    public void shouldUseValueFromCacheSuccessfullyWhenFlagIsOn() {
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        ICache<String, Boolean> readAuditLogSwitchCache = new VmCache<>(100, 1);
        readAuditLogSwitchCache.put("is-read-audit-logs-enabled-dp", true);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        readAuditLogsConsumer.accept(auditPayload);

        verify(logger).audit(auditPayload);
        verifyNoMoreInteractions(partitionService);
    }

    @Test
    public void shouldUseValueFromCacheSuccessfullyWhenFlagIsOff() {
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        ICache<String, Boolean> readAuditLogSwitchCache = new VmCache<>(100, 1);
        readAuditLogSwitchCache.put("is-read-audit-logs-enabled-dp", false);
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);

        readAuditLogsConsumer.accept(auditPayload);

        verifyNoMoreInteractions(logger, partitionService);
    }

    @Test
    public void shouldGetValueFromPartitionService() {
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("false");
        Map<String, Property> map = new HashMap<>();
        map.put("is-read-audit-logs-enabled", property);
        PartitionInfo partitionInfo = new PartitionInfo();
        partitionInfo.setProperties(map);
        Mockito.when(partitionService.getPartition("dp")).thenReturn(partitionInfo);
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);

        readAuditLogsConsumer.accept(auditPayload);

        verify(partitionService).getPartition("dp");
        verify(dpsHeaders, Mockito.times(3)).getPartitionId();
        verify(logger).info("PartitionInfo of dp has is-read-audit-logs-enabled flag as false");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldUseDefaultValueIfValueFromPartitionIsInvalid() {
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("no");
        Map<String, Property> map = new HashMap<>();
        map.put("is-read-audit-logs-enabled", property);
        PartitionInfo partitionInfo = new PartitionInfo();
        partitionInfo.setProperties(map);
        Mockito.when(partitionService.getPartition("dp")).thenReturn(partitionInfo);
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);

        readAuditLogsConsumer.accept(auditPayload);

        verify(partitionService).getPartition("dp");
        verify(dpsHeaders, Mockito.times(2)).getPartitionId();
        verify(logger).audit(auditPayload);
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldUseCachedValueWhenItsFalse() {
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("false");
        Map<String, Property> map = new HashMap<>();
        map.put("is-read-audit-logs-enabled", property);
        PartitionInfo partitionInfo = new PartitionInfo();
        partitionInfo.setProperties(map);
        Mockito.when(partitionService.getPartition("dp")).thenReturn(partitionInfo);
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);

        readAuditLogsConsumer.accept(auditPayload);
        // on second time should use cached value
        readAuditLogsConsumer.accept(auditPayload);

        verify(partitionService).getPartition("dp");
        verify(dpsHeaders, Mockito.times(4)).getPartitionId();
        verify(logger).info("PartitionInfo of dp has is-read-audit-logs-enabled flag as false");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldUseCachedValueWhenItsTrue() {
        Property property = new Property();
        property.setSensitive(false);
        property.setValue("True");
        Map<String, Property> map = new HashMap<>();
        map.put("is-read-audit-logs-enabled", property);
        PartitionInfo partitionInfo = new PartitionInfo();
        partitionInfo.setProperties(map);
        Mockito.when(partitionService.getPartition("dp")).thenReturn(partitionInfo);
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("dp");
        AuditPayload auditPayload = Mockito.mock(AuditPayload.class);
        ReflectionTestUtils.setField(readAuditLogsConsumer,"readAuditLogSwitchCache", readAuditLogSwitchCache);

        readAuditLogsConsumer.accept(auditPayload);
        // on second time should use cached value
        readAuditLogsConsumer.accept(auditPayload);

        verify(partitionService).getPartition("dp");
        verify(dpsHeaders, Mockito.times(4)).getPartitionId();
        verify(logger).info("PartitionInfo of dp has is-read-audit-logs-enabled flag as true");
        verify(logger, Mockito.times(2)).audit(auditPayload);
        verifyNoMoreInteractions(logger);
    }
}
