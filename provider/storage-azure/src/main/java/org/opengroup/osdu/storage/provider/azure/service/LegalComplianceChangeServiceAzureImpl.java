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

package org.opengroup.osdu.storage.provider.azure.service;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
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
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.azure.MessageBusImpl;
import org.opengroup.osdu.storage.provider.azure.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;

@Component
public class LegalComplianceChangeServiceAzureImpl implements ILegalComplianceChangeService {
    private static final String LEGAL_STATUS_INVALID = "Invalid";
    private static final Logger LOGGER = LoggerFactory.getLogger(LegalComplianceChangeServiceAzureImpl.class);
    private static final ComplianceChangeInfo UPDATE_CHANGE = new ComplianceChangeInfo(LegalCompliance.compliant, OperationType.update, RecordState.active);
    private static final ComplianceChangeInfo DELETE_CHANGE = new ComplianceChangeInfo(LegalCompliance.incompliant, OperationType.delete, RecordState.deleted);
    @Autowired
    private IRecordsMetadataRepository recordsRepo;
    @Autowired
    private DpsHeaders headers;
    @Autowired
    private LegalTagCache legalTagCache;
    @Autowired
    private MessageBusImpl pubSubclient;
    @Autowired
    private IFeatureFlag collaborationFeatureFlag;


    @Override
    public Map<String, LegalCompliance> updateComplianceOnRecords(LegalTagChangedCollection legalTagsChanged,
                                                                  DpsHeaders headers) throws ComplianceUpdateStoppedException {
        Map<String, LegalCompliance> output = new HashMap<>();
        Optional<CollaborationContext> collaborationContext = Optional.empty();

        LinkedHashMap <ComplianceChangeInfo, ArrayList<String>> complianceChangeInfoMap = new LinkedHashMap<ComplianceChangeInfo, ArrayList<String>>();
        complianceChangeInfoMap.put(UPDATE_CHANGE, new ArrayList<String>());
        complianceChangeInfoMap.put(DELETE_CHANGE, new ArrayList<String>());
        for (LegalTagChanged lt : legalTagsChanged.getStatusChangedTags()) {
            ComplianceChangeInfo complianceChangeInfo = this.getComplianceChangeInfo(lt);
            if (complianceChangeInfo != null) {
                complianceChangeInfoMap.get(complianceChangeInfo).add(lt.getChangedTagName());
            }
        }

        for (ComplianceChangeInfo complianceChangeInfo : complianceChangeInfoMap.keySet()) {
            String cursor = null;
            do {
                String[] legalTags = complianceChangeInfoMap.get(complianceChangeInfo).toArray(new String[0]);
                if (legalTags.length == 0) {
                    continue;
                }
                //TODO replace with the new method queryByLegal
                AbstractMap.SimpleEntry<String, List<RecordMetadata>> results = recordsRepo
                        .queryByLegalTagName(legalTags, 500, cursor);
                cursor = results.getKey();
                List<String> recordIds = new ArrayList<>();
                if (results.getValue() != null && !results.getValue().isEmpty()) {
                    List<RecordMetadata> recordsMetadata = results.getValue();
                    PubSubInfo[] pubsubInfos = this.updateComplianceStatus(complianceChangeInfo, recordsMetadata, output);
                    RecordChangedV2[] recordsChangedV2s = this.updateComplianceStatusRecordsChangedV2(complianceChangeInfo, recordsMetadata, output);
                    try {
                        this.recordsRepo.createOrUpdate(recordsMetadata, Optional.empty());
                    } catch (Exception e) {
                        logOnFailedUpdateRecords(legalTags, complianceChangeInfo.getPubSubEvent(), e);
                        throw new AppException(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error updating records upon legaltag changed.",
                                "The server could not process your request at the moment.", e);
                    }
                    for (RecordMetadata recordMetadata : recordsMetadata) {
                        recordIds.add(recordMetadata.getId());
                    }
                    if (collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
                        this.pubSubclient.publishMessage(collaborationContext, headers, recordsChangedV2s);
                    }
                    if (!collaborationContext.isPresent()) {
                        this.pubSubclient.publishMessage(headers, pubsubInfos);
                    }
                    logOnSucceedUpdateRecords(legalTags, complianceChangeInfo.getPubSubEvent(), recordIds);
                }
            } while (cursor != null);
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

    private RecordChangedV2[] updateComplianceStatusRecordsChangedV2(ComplianceChangeInfo complianceChangeInfo,
                                                                     List<RecordMetadata> recordMetadata, Map<String, LegalCompliance> output) {

        RecordChangedV2[] recordChangedV2 = new RecordChangedV2[recordMetadata.size()];

        int i = 0;
        for (RecordMetadata rm : recordMetadata) {
            rm.getLegal().setStatus(complianceChangeInfo.getNewState());
            rm.setStatus(complianceChangeInfo.getNewRecordState());
            recordChangedV2[i] = RecordChangedV2.builder()
                    .id(rm.getId())
                    .version(rm.getLatestVersion())
                    .modifiedBy(rm.getModifyUser())
                    .kind(rm.getKind())
                    .op(complianceChangeInfo.getPubSubEvent())
                    .build();
            output.put(rm.getId(), complianceChangeInfo.getNewState());
            i++;
        }

        return recordChangedV2;
    }

    private ComplianceChangeInfo getComplianceChangeInfo(LegalTagChanged lt) {
        ComplianceChangeInfo output = null;

        if (lt.getChangedTagStatus().equalsIgnoreCase("compliant")) {
            output = UPDATE_CHANGE;
        } else if (lt.getChangedTagStatus().equalsIgnoreCase("incompliant")) {
            this.legalTagCache.delete(lt.getChangedTagName());
            output = DELETE_CHANGE;
        } else {
            LOGGER.warn(String.format("Unknown LegalTag compliance status received %s %s",
                    lt.getChangedTagStatus(), lt.getChangedTagName()));
        }
        return output;
    }

    private void logOnSucceedUpdateRecords(String[] legalTags, OperationType opType, List<String> recordIds) {

        if (opType == OperationType.delete) {
            LOGGER.info("{} Records deleted successfully {} for legal tag(s) {}", recordIds.size(), Arrays.toString(recordIds.toArray()), String.join(", ", legalTags));
        } else {
            LOGGER.info("{} Records updated successfully {} for legal tag(s) {}", recordIds.size(), Arrays.toString(recordIds.toArray()), String.join(", ", legalTags));
        }
    }

    private void logOnFailedUpdateRecords(String[] legalTags, OperationType opType, Exception e) {
        if (opType == OperationType.delete) {
            LOGGER.error("Failed to delete records cause of error {} for legal tag(s) {}", e.getMessage(), String.join(", ", legalTags));
        } else {
            LOGGER.error("Failed to update records cause of error {} for legal tag(s) {}", e.getMessage(), String.join(", ", legalTags));
        }
    }

}
