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

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelperFactory;
import org.opengroup.osdu.core.aws.v2.dynamodb.DynamoDBQueryHelper;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.aws.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.aws.util.WorkerThreadPool;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.LegalTagAssociationDoc;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class LegalComplianceChangeServiceAWSImplTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private LegalComplianceChangeServiceAWSImpl service;

    @Mock
    private CollaborationContextFactory collaborationContextFactory;

    @Value("${aws.dynamodb.legalTagTable.ssm.relativePath}")
    String legalTagTableParameterRelativePath;

    @Mock
    private WorkerThreadPool workerThreadPool;

    @Captor
    private ArgumentCaptor<List<LegalTagAssociationDoc>> ltaCaptor;

    @Mock
    private DynamoDBQueryHelperFactory dynamoDBQueryHelperFactory;

    @Mock
    private DynamoDBQueryHelper legalTagAssociationHelper;

    @Mock
    private IRecordsMetadataRepository<String> recordsMetadataRepository;

    @Mock
    private IMessageBus storageMessageBus;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private LegalTagCache legalTagCache;

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    @Test
    void updateComplianceOnRecordsTest() throws ComplianceUpdateStoppedException {
        // arrange
        String incompliantTagName = "incompliant-test-tag";
        String incompliantRecordId = "incompliant-record";
        String compliantTagName = "compliant-test-tag";
        String compliantRecordId = "compliant-record";

        when(collaborationContextFactory.create(anyString())).thenReturn(Optional.empty());
        when(dynamoDBQueryHelperFactory.createQueryHelper(any(DpsHeaders.class), eq(null), any())).thenReturn(legalTagAssociationHelper);

        // create parameters
        LegalTagChangedCollection legalTagsChanged = new LegalTagChangedCollection();
        List<LegalTagChanged> legalTagChangedList = new ArrayList<>();
        LegalTagChanged incompliantLegalTagChanged = new LegalTagChanged();
        incompliantLegalTagChanged.setChangedTagName(incompliantTagName);
        incompliantLegalTagChanged.setChangedTagStatus("incompliant");
        legalTagChangedList.add(incompliantLegalTagChanged);
        LegalTagChanged compliantLegalTagChanged = new LegalTagChanged();
        compliantLegalTagChanged.setChangedTagName(compliantTagName);
        compliantLegalTagChanged.setChangedTagStatus("compliant");
        legalTagChangedList.add(compliantLegalTagChanged);
        legalTagsChanged.setStatusChangedTags(legalTagChangedList);

        DpsHeaders headers = new DpsHeaders();

        // compliant record(s)
        RecordMetadata compliantRecordMetadata = new RecordMetadata();
        compliantRecordMetadata.setId(compliantRecordId);
        Legal compliantLegal = new Legal();
        Set<String> compliantLegalTags = new HashSet<>();
        compliantLegalTags.add(compliantTagName);
        compliantLegal.setLegaltags(compliantLegalTags);
        compliantRecordMetadata.setLegal(compliantLegal);

        // incompliant record(s)
        String cursor = null;
        RecordMetadata incompliantRecordMetadata = new RecordMetadata();
        incompliantRecordMetadata.setId(incompliantRecordId);
        Legal incompliantLegal = new Legal();
        Set<String> incompliantLegalTags = new HashSet<>();
        incompliantLegalTags.add(incompliantTagName);
        incompliantLegal.setLegaltags(incompliantLegalTags);
        incompliantRecordMetadata.setLegal(incompliantLegal);
        List<RecordMetadata> incompliantRecordMetaDatas = new ArrayList<>();
        incompliantRecordMetaDatas.add(incompliantRecordMetadata);
        incompliantRecordMetaDatas.add(compliantRecordMetadata);
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> incompliantResult =
                new AbstractMap.SimpleEntry<String, List<RecordMetadata>>(cursor, incompliantRecordMetaDatas);

        List<RecordMetadata> compliantRecordMetaDatas = new ArrayList<>();
        compliantRecordMetaDatas.add(compliantRecordMetadata);
        compliantRecordMetaDatas.add(incompliantRecordMetadata);
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> compliantResult =
                new AbstractMap.SimpleEntry<String, List<RecordMetadata>>(cursor, compliantRecordMetaDatas);

        // incompliant pub sub info
        PubSubInfo incompliantPubSubInfo = new PubSubInfo();
        incompliantPubSubInfo.setId(incompliantRecordId);
        incompliantPubSubInfo.setOp(OperationType.delete);
        PubSubInfo[] incompliantPubSubInfos = new PubSubInfo[1];
        incompliantPubSubInfos[0] = incompliantPubSubInfo;

        // compliant pub sub info
        PubSubInfo compliantPubSubInfo = new PubSubInfo();
        compliantPubSubInfo.setId(compliantRecordId);
        compliantPubSubInfo.setOp(OperationType.update);
        PubSubInfo[] compliantPubSubInfos = new PubSubInfo[1];
        compliantPubSubInfos[0] = compliantPubSubInfo;

        // expected output
        Map<String, LegalCompliance> expectedOutput = new HashMap<>();
        expectedOutput.put(incompliantRecordId, LegalCompliance.incompliant);
        expectedOutput.put(compliantRecordId, LegalCompliance.compliant);

        ArgumentCaptor<List<RecordMetadata>> recordMetadataCaptor = ArgumentCaptor.forClass(List.class);

        // mock methods called
        when(recordsMetadataRepository.queryByLegalTagName(incompliantTagName, 500, null))
                .thenReturn(incompliantResult);

        when(recordsMetadataRepository.queryByLegalTagName(compliantTagName, 500, null))
                .thenReturn(compliantResult);

        ArgumentCaptor<PubSubInfo[]> pubSubArg = ArgumentCaptor.forClass(PubSubInfo[].class);

        // act
        Map<String, LegalCompliance> output = service.updateComplianceOnRecords(legalTagsChanged, headers);

        // assert
        Mockito.verify(legalTagAssociationHelper, Mockito.times(2)).batchDelete(ltaCaptor.capture());
        Set<String> ids = new HashSet<>();
        for (List<LegalTagAssociationDoc> ltas : ltaCaptor.getAllValues()) {
            Assertions.assertEquals(1, ltas.size());
            LegalTagAssociationDoc lta = ltas.get(0);
            ids.add(lta.getRecordIdLegalTag());
        }
        Assertions.assertEquals(2, ids.size());
        Assertions.assertTrue(ids.contains(LegalTagAssociationDoc.getLegalRecordId(compliantRecordId, incompliantTagName)));
        Assertions.assertTrue(ids.contains(LegalTagAssociationDoc.getLegalRecordId(incompliantRecordId, compliantTagName)));

        // that create is called on the record returned for compliant
        Mockito.verify(recordsMetadataRepository, Mockito.times(2)).createOrUpdate(recordMetadataCaptor.capture(), eq(Optional.empty()));

        // that storageMessageBus publishMessage is called with the right pubsubinfos
        Mockito.verify(storageMessageBus, Mockito.times(2))
                .publishMessage(Mockito.any(), pubSubArg.capture());
        List<PubSubInfo[]> captured = pubSubArg.getAllValues();
        Object[] incompliantPubSubObj = captured.get(0);
        PubSubInfo incompliantPubSub = (PubSubInfo) incompliantPubSubObj[0];
        Object[] compliantPubSubObj = captured.get(1);
        PubSubInfo compliantPubSub = (PubSubInfo) compliantPubSubObj[0];

        Assert.assertEquals(incompliantPubSubInfos[0].getId(), incompliantPubSub.getId());
        Assert.assertEquals(incompliantPubSubInfos[0].getOp(), incompliantPubSub.getOp());
        Assert.assertEquals(compliantPubSubInfos[0].getId(), compliantPubSub.getId());
        Assert.assertEquals(compliantPubSubInfos[0].getOp(), compliantPubSub.getOp());

        // that output returned is expected
        Assert.assertEquals(output.get(incompliantRecordId), expectedOutput.get(incompliantRecordId));
        Assert.assertEquals(output.get(compliantRecordId), expectedOutput.get(compliantRecordId));
    }
}
