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

package org.opengroup.osdu.storage.provider.aws.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.aws.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.aws.jobs.LegalComplianceChangeServiceAWSImpl;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class LegalComplianceChangeServiceAWSImplTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private LegalComplianceChangeServiceAWSImpl service = new LegalComplianceChangeServiceAWSImpl();

    @Mock
    private IRecordsMetadataRepository repo;

    @Mock
    private IMessageBus storageMessageBus;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private StorageAuditLogger auditLogger;

    @Mock
    private LegalTagCache legalTagCache;

    @Test
    public void updateComplianceOnRecordsTest() throws ComplianceUpdateStoppedException {
        // arrange
        String incompliantTagName = "incompliant-test-tag";
        String incompliantRecordId = "incompliant-record";
        String compliantTagName = "compliant-test-tag";
        String compliantRecordId = "compliant-record";

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

        // incompliant record(s)
        String cursor = null;
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(incompliantRecordId);
        Legal incompliantLegal = new Legal();
        Set<String> incompliantLegalTags = new HashSet<>();
        incompliantLegalTags.add("incompliant-tag");
        incompliantLegal.setLegaltags(incompliantLegalTags);
        recordMetadata.setLegal(incompliantLegal);
        List<RecordMetadata> incompliantRecordMetaDatas = new ArrayList<>();
        incompliantRecordMetaDatas.add(recordMetadata);
        AbstractMap.SimpleEntry<String, List<RecordMetadata>> incompliantResult =
                new AbstractMap.SimpleEntry<String, List<RecordMetadata>>(cursor, incompliantRecordMetaDatas);

        // compliant record(s)
        RecordMetadata compliantRecordMetadata = new RecordMetadata();
        compliantRecordMetadata.setId(compliantRecordId);
        Legal compliantLegal = new Legal();
        Set<String> compliantLegalTags = new HashSet<>();
        compliantLegalTags.add("compliant-tag");
        compliantLegal.setLegaltags(compliantLegalTags);
        compliantRecordMetadata.setLegal(compliantLegal);
        List<RecordMetadata> compliantRecordMetaDatas = new ArrayList<>();
        compliantRecordMetaDatas.add(compliantRecordMetadata);
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
        compliantPubSubInfo.setOp(OperationType.create);
        PubSubInfo[] compliantPubSubInfos = new PubSubInfo[1];
        compliantPubSubInfos[0] = compliantPubSubInfo;

        // expected output
        Map<String, LegalCompliance> expectedOutput = new HashMap<>();
        expectedOutput.put(incompliantRecordId, LegalCompliance.incompliant);
        expectedOutput.put(compliantRecordId, LegalCompliance.compliant);

        // mock methods called
        Mockito.when(repo.queryByLegalTagName(Mockito.eq(incompliantTagName), Mockito.eq(500), Mockito.any()))
                .thenReturn(incompliantResult);

        Mockito.when(repo.queryByLegalTagName(Mockito.eq(compliantTagName), Mockito.eq(500), Mockito.any()))
                .thenReturn(compliantResult);

        ArgumentCaptor<PubSubInfo[]> pubSubArg = ArgumentCaptor.forClass(PubSubInfo[].class);

        // act
        Map<String, LegalCompliance> output = service.updateComplianceOnRecords(legalTagsChanged, headers);

        // assert
        // that delete is called on the record returned for incompliant
        Mockito.verify(repo, Mockito.times(1)).delete(incompliantRecordId);
        // that create is called on the record returned for compliant
        Mockito.verify(repo, Mockito.times(1)).createOrUpdate(compliantRecordMetaDatas);

        // that storageMessageBus publishMessage is called with the right pubsubinfos
        Mockito.verify(storageMessageBus, Mockito.times(2))
                .publishMessage(Mockito.any(), pubSubArg.capture());
        List<PubSubInfo[]> captured = pubSubArg.getAllValues();
        Object incompliantPubSubObj = captured.get(0);
        PubSubInfo incompliantPubSub = (PubSubInfo) incompliantPubSubObj;
        Object compliantPubSubObj = captured.get(1);
        PubSubInfo compliantPubSub = (PubSubInfo) compliantPubSubObj;

        Assert.assertEquals(incompliantPubSubInfos[0].getId(), incompliantPubSub.getId());
        Assert.assertEquals(incompliantPubSubInfos[0].getOp(), incompliantPubSub.getOp());
        Assert.assertEquals(compliantPubSubInfos[0].getId(), compliantPubSub.getId());
        Assert.assertEquals(compliantPubSubInfos[0].getOp(), compliantPubSub.getOp());

        // that output returned is expected
        Assert.assertEquals(output.get(incompliantRecordId), expectedOutput.get(incompliantRecordId));
        Assert.assertEquals(output.get(compliantRecordId), expectedOutput.get(compliantRecordId));
    }
}
