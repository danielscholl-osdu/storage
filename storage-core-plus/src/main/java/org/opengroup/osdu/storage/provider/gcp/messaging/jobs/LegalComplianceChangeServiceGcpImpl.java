/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs;

import static java.util.Collections.singletonList;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceChangeInfo;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.ILegalComplianceChangeService;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegalComplianceChangeServiceGcpImpl implements ILegalComplianceChangeService {

    private final IRecordsMetadataRepository recordsRepo;
    private final IMessageBus messageBus;
    private final StorageAuditLogger auditLogger;
    private final JaxRsDpsLog logger;
    // not conventional field name due to bean qualifiers in a core module
    private final ICache<String, String> LegalTagCache;

    private final long maxRunningTimeMills = 115000;

    @Override
    public Map<String, LegalCompliance> updateComplianceOnRecords(LegalTagChangedCollection legalTagsChanged,
        DpsHeaders headers) throws ComplianceUpdateStoppedException {
        Map<String, LegalCompliance> output = new HashMap<>();
        long currentTimeMills;
        long start = System.currentTimeMillis();

        for (LegalTagChanged lt : legalTagsChanged.getStatusChangedTags()) {

            ComplianceChangeInfo complianceChangeInfo = this.getComplianceChangeInfo(lt);
            if (complianceChangeInfo == null) {
                continue;
            }
            AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = this.recordsRepo
                .queryByLegal(lt.getChangedTagName(), complianceChangeInfo.getCurrent(), 500);

            while (results.getValue() != null && !results.getValue().isEmpty()) {
                currentTimeMills = System.currentTimeMillis() - start;
                if (currentTimeMills >= maxRunningTimeMills) {
                    throw new ComplianceUpdateStoppedException(currentTimeMills / 1000);
                }
                List<RecordMetadata> recordsMetadata = results.getValue();
                PubSubInfo[] pubsubInfos = this.updateComplianceStatus(complianceChangeInfo, recordsMetadata, output);
                StringBuilder recordsId = new StringBuilder();
                for (RecordMetadata recordMetadata : recordsMetadata) {
                    recordsId.append(", ").append(recordMetadata.getId());
                }
                this.recordsRepo.createOrUpdate(recordsMetadata, Optional.empty());
                this.messageBus.publishMessage(headers, pubsubInfos);
                this.auditLogger.updateRecordsComplianceStateSuccess(
                    singletonList("[" + recordsId.substring(2) + "]"));
                results = this.recordsRepo.queryByLegal(lt.getChangedTagName(), complianceChangeInfo.getCurrent(), 500);
            }
        }

        return output;
    }

    private PubSubInfo[] updateComplianceStatus(ComplianceChangeInfo complianceChangeInfo,
        List<RecordMetadata> recordMetadata, Map<String, LegalCompliance> output) {

        PubSubInfo[] pubsubInfo = new PubSubInfo[recordMetadata.size()];

        int i = 0;
        for (RecordMetadata rm : recordMetadata) {
            rm.getLegal().setStatus(complianceChangeInfo.getNewState());
            rm.setStatus(complianceChangeInfo.getNewRecordState());
            pubsubInfo[i] = new PubSubInfo(rm.getId(), rm.getKind(), complianceChangeInfo.getPubSubEvent());
            output.put(rm.getId(), complianceChangeInfo.getNewState());
            i++;
        }

        return pubsubInfo;
    }

    private ComplianceChangeInfo getComplianceChangeInfo(LegalTagChanged lt) {
        ComplianceChangeInfo output = null;

        if (lt.getChangedTagStatus().equalsIgnoreCase("compliant")) {
            output = new ComplianceChangeInfo(LegalCompliance.compliant, OperationType.update, RecordState.active);
        } else if (lt.getChangedTagStatus().equalsIgnoreCase("incompliant")) {
            this.LegalTagCache.delete(lt.getChangedTagName());
            output = new ComplianceChangeInfo(LegalCompliance.incompliant, OperationType.delete, RecordState.deleted);
        } else {
            this.logger.warning(String.format("Unknown LegalTag compliance status received %s %s",
                lt.getChangedTagStatus(), lt.getChangedTagName()));
        }

        return output;
    }
}
