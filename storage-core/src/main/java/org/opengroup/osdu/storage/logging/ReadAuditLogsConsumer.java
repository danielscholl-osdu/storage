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

import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.VmCache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.storage.service.IPartitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class ReadAuditLogsConsumer implements Consumer<AuditPayload> {

    private static final String READ_AUDIT_LOGS_SWITCH_NAME = "is-read-audit-logs-enabled";
    private static final boolean READ_AUDIT_LOGS_SWITCH_DEFAULT_STATE = true;
    private static final int CACHE_SIZE = 1000;

    @Value("${partition.property.refresh.rate:60}")
    private int partitionInfoPropertyRefreshRate;

    private ICache<String, Boolean> readAuditLogSwitchCache;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private DpsHeaders dpsHeaders;

    @Autowired
    private IPartitionService partitionService;

    @PostConstruct
    private void setup() {
        readAuditLogSwitchCache = new VmCache<>(partitionInfoPropertyRefreshRate, CACHE_SIZE);
    }

    @Override
    public void accept(AuditPayload auditPayload) {
        if (isReadAuditLogsTurnedOn()) {
            logger.audit(auditPayload);
        }
    }

    private boolean isReadAuditLogsTurnedOn() {
        String cacheKey = READ_AUDIT_LOGS_SWITCH_NAME + "-" + dpsHeaders.getPartitionId();
        Boolean isReadAuditLogsTurnedOn = readAuditLogSwitchCache.get(cacheKey);
        if (isReadAuditLogsTurnedOn != null) {
            return isReadAuditLogsTurnedOn;
        }
        isReadAuditLogsTurnedOn = getFromPartitionService();
        readAuditLogSwitchCache.put(cacheKey, isReadAuditLogsTurnedOn);
        return isReadAuditLogsTurnedOn;
    }

    private boolean getFromPartitionService() {
        try {
            PartitionInfo partitionInfo = partitionService.getPartition(dpsHeaders.getPartitionId());
            if (partitionInfo.getProperties().containsKey(READ_AUDIT_LOGS_SWITCH_NAME)) {
                Optional<Boolean> isReadAuditLogsOn = getReadAuditLogsSwitchValue(partitionInfo);
                isReadAuditLogsOn.ifPresent(switchState -> logger.debug(String.format("PartitionInfo of %s has %s flag as %b",
                        dpsHeaders.getPartitionId(), READ_AUDIT_LOGS_SWITCH_NAME, switchState)));
                return isReadAuditLogsOn.orElse(READ_AUDIT_LOGS_SWITCH_DEFAULT_STATE);
            }
        } catch (Exception e) {
            logger.error("Can't get partition info for partition: " + dpsHeaders.getPartitionId(), e);
        }
        return READ_AUDIT_LOGS_SWITCH_DEFAULT_STATE;
    }

    /**
     * Parsing value to Boolean
     * Boolean#parseBoolean does not fit, because such values as 'no' or 'off' will be converted to false
     */
    private Optional<Boolean> getReadAuditLogsSwitchValue(PartitionInfo partitionInfo) {
        String value = (String)partitionInfo.getProperties().get(READ_AUDIT_LOGS_SWITCH_NAME).getValue();
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Optional.of(Boolean.parseBoolean(value));
        }
        return Optional.empty();
    }
}
