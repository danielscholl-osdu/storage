/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.provider.reference.jobs;

import static java.util.Collections.singletonList;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.opengroup.osdu.storage.provider.reference.cache.LegalTagCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LegalComplianceChangeServiceImpl implements ILegalComplianceChangeService {

  private static final Logger LOG = LoggerFactory.getLogger(LegalComplianceChangeServiceImpl.class);

  @Autowired
  private IRecordsMetadataRepository recordsRepo;

  @Autowired
  private IMessageBus pubSubClient;

  @Autowired
  private StorageAuditLogger auditLogger;

  @Autowired
  private LegalTagCache legalTagCache;

  private long maxRunningTimeMills = 115000;

  @Override
  public Map<String, LegalCompliance> updateComplianceOnRecords(
      LegalTagChangedCollection legalTagsChanged,
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
        PubSubInfo[] pubsubInfos = this
            .updateComplianceStatus(complianceChangeInfo, recordsMetadata, output);
        StringBuilder recordsId = new StringBuilder();
        for (RecordMetadata recordMetadata : recordsMetadata) {
          recordsId.append(", ").append(recordMetadata.getId());
        }
        this.recordsRepo.createOrUpdate(recordsMetadata);
        this.pubSubClient.publishMessage(headers, pubsubInfos);
        this.auditLogger.updateRecordsComplianceStateSuccess(
            singletonList("[" + recordsId.toString().substring(2) + "]"));
        results = this.recordsRepo
            .queryByLegal(lt.getChangedTagName(), complianceChangeInfo.getCurrent(), 500);
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
      pubsubInfo[i] = new PubSubInfo(rm.getId(), rm.getKind(),
          complianceChangeInfo.getPubSubEvent());
      output.put(rm.getId(), complianceChangeInfo.getNewState());
      i++;
    }

    return pubsubInfo;
  }

  private ComplianceChangeInfo getComplianceChangeInfo(LegalTagChanged lt) {
    ComplianceChangeInfo output = null;

    if (lt.getChangedTagStatus().equalsIgnoreCase("compliant")) {
      output = new ComplianceChangeInfo(LegalCompliance.compliant, OperationType.create,
          RecordState.active);
    } else if (lt.getChangedTagStatus().equalsIgnoreCase("incompliant")) {
      this.legalTagCache.delete(lt.getChangedTagName());
      output = new ComplianceChangeInfo(LegalCompliance.incompliant, OperationType.delete,
          RecordState.deleted);
    } else {
      LOG.warn(String.format("Unknown LegalTag compliance status received %s %s",
          lt.getChangedTagStatus(), lt.getChangedTagName()));
    }

    return output;
  }
}
