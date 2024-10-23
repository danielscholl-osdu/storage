// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws.jobs;

import java.util.AbstractMap;
import java.util.ArrayList;
import static java.util.Collections.singletonList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelperV2;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
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
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.aws.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.inject.Inject;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Service
public class LegalComplianceChangeServiceAWSImpl implements ILegalComplianceChangeService {

    private final static String INCOMPLIANT_STRING = "incompliant";
    private final static String COMPLIANT_STRING = "compliant";

    @Autowired
    private CollaborationContextFactory collaborationContextFactory;

    @Value("${aws.dynamodb.legalTagTable.ssm.relativePath}")
    String legalTagTableParameterRelativePath;

    @Inject
    private WorkerThreadPool workerThreadPool;

    @Inject
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Autowired
    private IRecordsMetadataRepository<String> recordsMetadataRepository;

    @Autowired
    private IMessageBus storageMessageBus;

    @Autowired
    private StorageAuditLogger auditLogger;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    private LegalTagCache legalTagCache;

    @Override
    public Map<String, LegalCompliance> updateComplianceOnRecords(LegalTagChangedCollection legalTagsChanged,
                                                                  DpsHeaders headers) throws ComplianceUpdateStoppedException {
        Map<String, LegalCompliance> output = new HashMap<>();

        Optional<CollaborationContext> collaborationContext = collaborationContextFactory.create(headers.getCollaboration());
        DynamoDBQueryHelperV2 legalTagQueryHelper = dynamoDBQueryHelperFactory.getQueryHelperForPartition(headers, legalTagTableParameterRelativePath, workerThreadPool.getClientConfiguration());

        for (LegalTagChanged lt : legalTagsChanged.getStatusChangedTags()) {
            updateComplianceGivenLegalTag(lt, headers, output, collaborationContext, legalTagQueryHelper);
        }

        return output;
    }

    private void updateComplianceGivenLegalTag(LegalTagChanged lt, DpsHeaders headers, Map<String, LegalCompliance> output, Optional<CollaborationContext> context,
                                               DynamoDBQueryHelperV2 legalTagQueryHelper) {
        ComplianceChangeInfo complianceChangeInfo = this.getComplianceChangeInfo(lt);
        if (complianceChangeInfo == null) {
            return;
        }

        AbstractMap.SimpleEntry<String, List<RecordMetadata>> results;
        ArrayList<String> recordLegalTagsToDelete = new ArrayList<>(500);
        ArrayList<RecordMetadata> modifiedRecords = new ArrayList<>(500);
        String cursor = null;
        do {
            results = this.recordsMetadataRepository
                .queryByLegalTagName(lt.getChangedTagName(), 500, cursor);
            cursor = results.getKey();
            List<RecordMetadata> recordsMetadata = results.getValue();
            ArrayList<PubSubInfo> pubsubInfos = this.updateComplianceStatus(complianceChangeInfo, lt.getChangedTagName(), recordsMetadata, output, recordLegalTagsToDelete, modifiedRecords);

            this.recordsMetadataRepository.createOrUpdate(modifiedRecords, context);

            StringBuilder recordsId = new StringBuilder();
            for (RecordMetadata recordMetadata : modifiedRecords) {
                recordsId.append(", ").append(recordMetadata.getId());
            }
            this.auditLogger.updateRecordsComplianceStateSuccess(
                singletonList("[" + recordsId.toString() + "]"));

            List<LegalTagAssociationDoc> legalTagRecordAssociation = recordLegalTagsToDelete.stream().map(recordId -> LegalTagAssociationDoc.createLegalTagDoc(lt.getChangedTagName(), recordId)).collect(Collectors.toList());
            legalTagQueryHelper.batchDelete(legalTagRecordAssociation);
            this.storageMessageBus.publishMessage(headers, pubsubInfos.toArray(new PubSubInfo[0]));
            recordLegalTagsToDelete.clear();
            modifiedRecords.clear();
        } while (cursor != null);
    }

    private ArrayList<PubSubInfo> updateComplianceStatus(ComplianceChangeInfo complianceChangeInfo,
                                                         String legalTagName, List<RecordMetadata> recordMetadata, Map<String, LegalCompliance> output, List<String> notCurrentRecords,
                                                         List<RecordMetadata> modifiedRecords) {
        ArrayList<PubSubInfo> pubsubInfo = new ArrayList<>(recordMetadata.size());

        for (RecordMetadata rm : recordMetadata) {
            if (rm.getLegal().getLegaltags().contains(legalTagName)) {
                rm.getLegal().setStatus(complianceChangeInfo.getNewState());
                rm.setStatus(complianceChangeInfo.getNewRecordState());
                pubsubInfo.add(new PubSubInfo(rm.getId(), rm.getKind(), complianceChangeInfo.getPubSubEvent()));
                output.put(rm.getId(), complianceChangeInfo.getNewState());
                modifiedRecords.add(rm);
            } else {
                notCurrentRecords.add(rm.getId());
            }
        }

        return pubsubInfo;
    }

    private ComplianceChangeInfo getComplianceChangeInfo(LegalTagChanged lt) {
        ComplianceChangeInfo output = null;

        if (lt.getChangedTagStatus().equalsIgnoreCase(COMPLIANT_STRING)) {
            output = new ComplianceChangeInfo(LegalCompliance.compliant, OperationType.update, RecordState.active);
        } else if (lt.getChangedTagStatus().equalsIgnoreCase(INCOMPLIANT_STRING)) {
            this.legalTagCache.delete(lt.getChangedTagName());
            output = new ComplianceChangeInfo(LegalCompliance.incompliant, OperationType.delete, RecordState.deleted);
        } else {
            this.logger.warning(String.format("Unknown LegalTag compliance status received %s %s",
                    lt.getChangedTagStatus(), lt.getChangedTagName()));
        }

        return output;
    }
}
