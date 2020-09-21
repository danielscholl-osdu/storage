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

import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.aws.dynamodb.DynamoDBQueryHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.storage.*;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.util.dynamodb.RecordMetadataDoc;
import org.opengroup.osdu.storage.provider.aws.RecordsMetadataRepositoryImpl;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class RecordsMetadataRepositoryTest {

    @InjectMocks
    // Created inline instead of with autowired because mocks were overwritten
    // due to lazy loading
    private RecordsMetadataRepositoryImpl repo = new RecordsMetadataRepositoryImpl();

    @Mock
    private DynamoDBQueryHelper queryHelper;

    @Mock
    private UserAccessService userAccessService;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void createRecordMetadata() {
        // Arrange
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("opendes:id:15706318658560");
        recordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        recordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        recordMetadata.setStatus(recordStatus);

        String user = "test-user";
        recordMetadata.setUser(user);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(recordMetadata);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(recordMetadata.getId());
        expectedRmd.setKind(recordMetadata.getKind());
        expectedRmd.setLegaltags(recordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(recordMetadata.getStatus().toString());
        expectedRmd.setUser(recordMetadata.getUser());
        expectedRmd.setMetadata(recordMetadata);

        Mockito.doNothing().when(queryHelper).save(Mockito.eq(expectedRmd));

        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.eq(recordAcl))).thenReturn(true);

        // Act
        repo.createOrUpdate(recordsMetadata);

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).save(expectedRmd);
    }

    @Test
    public void getRecordMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";

        RecordMetadata expectedRecordMetadata = new RecordMetadata();
        expectedRecordMetadata.setId(id);
        expectedRecordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        expectedRecordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        expectedRecordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        expectedRecordMetadata.setStatus(recordStatus);

        String user = "test-user";
        expectedRecordMetadata.setUser(user);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(expectedRecordMetadata.getId());
        expectedRmd.setKind(expectedRecordMetadata.getKind());
        expectedRmd.setLegaltags(expectedRecordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(expectedRecordMetadata.getStatus().toString());
        expectedRmd.setUser(expectedRecordMetadata.getUser());
        expectedRmd.setMetadata(expectedRecordMetadata);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.anyString()))
                .thenReturn(expectedRmd);

        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.eq(recordAcl))).thenReturn(true);

        // Act
        RecordMetadata recordMetadata = repo.get(id);

        // Assert
        Assert.assertEquals(recordMetadata, expectedRecordMetadata);
    }

    @Test
    public void getRecordsMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";
        List<String> ids = new ArrayList<>();
        ids.add(id);

        RecordMetadata expectedRecordMetadata = new RecordMetadata();
        expectedRecordMetadata.setId(id);
        expectedRecordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        expectedRecordMetadata.setAcl(recordAcl);

        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        expectedRecordMetadata.setLegal(recordLegal);

        RecordState recordStatus = RecordState.active;
        expectedRecordMetadata.setStatus(recordStatus);

        String user = "test-user";
        expectedRecordMetadata.setUser(user);

        Map<String, RecordMetadata> expectedRecordsMetadata = new HashMap<>();
        expectedRecordsMetadata.put(id, expectedRecordMetadata);

        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        expectedRmd.setId(expectedRecordMetadata.getId());
        expectedRmd.setKind(expectedRecordMetadata.getKind());
        expectedRmd.setLegaltags(expectedRecordMetadata.getLegal().getLegaltags());
        expectedRmd.setStatus(expectedRecordMetadata.getStatus().toString());
        expectedRmd.setUser(expectedRecordMetadata.getUser());
        expectedRmd.setMetadata(expectedRecordMetadata);

        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.anyString()))
                .thenReturn(expectedRmd);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.eq(recordAcl))).thenReturn(true);

        // Act
        Map<String, RecordMetadata> recordsMetadata = repo.get(ids);

        // Assert
        Assert.assertEquals(recordsMetadata, expectedRecordsMetadata);
    }

    @Test
    public void deleteRecordMetadata() {
        // Arrange
        String id = "opendes:id:15706318658560";
        RecordMetadataDoc expectedRmd = new RecordMetadataDoc();
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("opendes:id:15706318658560");
        recordMetadata.setKind("opendes:source:type:1.0.0");

        Acl recordAcl = new Acl();
        String[] owners = {"data.tenant@byoc.local"};
        String[] viewers = {"data.tenant@byoc.local"};
        recordAcl.setOwners(owners);
        recordAcl.setViewers(viewers);
        recordMetadata.setAcl(recordAcl);
        Legal recordLegal = new Legal();
        Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-1570631865856"));
        recordLegal.setLegaltags(legalTags);
        LegalCompliance status = LegalCompliance.compliant;
        recordLegal.setStatus(status);
        Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
        recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
        recordMetadata.setLegal(recordLegal);
        expectedRmd.setMetadata(recordMetadata);

        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.doNothing().when(queryHelper).deleteByPrimaryKey(RecordMetadataDoc.class, id);
        Mockito.when(queryHelper.loadByPrimaryKey(Mockito.eq(RecordMetadataDoc.class), Mockito.anyString()))
                .thenReturn(expectedRmd);

        Mockito.when(userAccessService.userHasAccessToRecord(Mockito.eq(recordAcl))).thenReturn(true);

        // Act
        repo.delete(id);

        // Assert
        Mockito.verify(queryHelper, Mockito.times(1)).deleteByPrimaryKey(RecordMetadataDoc.class, id);
    }

    // TODO: Write test for queryByLegalTagName(String legalTagName, int limit, String cursor) once the method is finished
}
