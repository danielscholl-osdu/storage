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

package org.opengroup.osdu.storage.provider.aws.security;


import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.entitlements.EntitlementsService;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordProcessing;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.opengroup.osdu.storage.provider.aws.cache.GroupCache;
import org.opengroup.osdu.storage.provider.aws.util.CacheHelper;
import org.opengroup.osdu.storage.service.EntitlementsAndCacheServiceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


class UserAccessServiceTest {

    @InjectMocks
    private UserAccessService CUT = new UserAccessService();

    Acl acl;

    @Mock
    RecordMetadata record;

    @Mock
    EntitlementsAndCacheServiceImpl entitlementsExtension;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private CacheHelper cacheHelper;

    @Mock
    private GroupCache cache;

    @Mock 
    private EntitlementsService entitlementsService;

    @Mock
    private IEntitlementsFactory entitlementsFactory;

    @Mock
    private IServiceAccountJwtClient serviceAccountClient;

    private Groups groups = new Groups();

    private GroupInfo groupInfo = new GroupInfo();

    @BeforeEach
    void setUp() {
        openMocks(this);
        doNothing().when(record).setUser("not a user");

        List<GroupInfo> groupInfos = new ArrayList<>();
        groupInfo.setName("data.tenant");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        Mockito.when(entitlementsExtension.getGroups(Mockito.any())).thenReturn(groups);

        ReflectionTestUtils.setField(CUT, "dpsHeaders", dpsHeaders);
    }

    @Test
    void userHasAccessToRecord_authorizedUser_ReturnsTrue() {
        // Arrange
        acl = new Acl();
        String[] owners = { "data.tenant@byoc.local" };
        String[] viewers = { "data.tenant@byoc.local" };
        acl.setOwners(owners);
        acl.setViewers(viewers);
        doNothing().when(record).setAcl(acl);

        // Act
        boolean actual = CUT.userHasAccessToRecord(acl);

        // Assert
        Assert.assertTrue(actual);
    }

    @Test
    void userHasAccessToRecord_unauthorizedUser_ReturnsFalse() {
        // Arrange
        acl = new Acl();
        acl.setOwners(new String[] {});
        acl.setViewers(new String [] {});

        doNothing().when(record).setAcl(acl);

        // Act
        boolean actual = CUT.userHasAccessToRecord(acl);

        // Assert
        Assert.assertFalse(actual);
    }

    @Test
    void validateRecordAcl_shouldThrowAppException_whenInvalidGroupName() {
        // Arrange
        RecordProcessing recordProcessing = mock(RecordProcessing.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        Acl acl = mock(Acl.class);
        String[] aclList = {"invalidGroup@domain"};
        
        when(acl.flattenAcl(acl)).thenReturn(aclList);
        when(recordMetadata.getAcl()).thenReturn(acl);
        when(recordProcessing.getRecordMetadata()).thenReturn(recordMetadata);
        
        assertThrows(AppException.class, () -> {
            CUT.validateRecordAcl(recordProcessing);
        });
    }

    @Test
    void validateRecordAcl_shouldNotThrowException_whenValidGroupName() {
        // Arrange
        RecordProcessing recordProcessing = mock(RecordProcessing.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        Acl acl = mock(Acl.class);
        String[] aclList = {"data.tenant@byoc.local"};

        when(recordProcessing.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.getAcl()).thenReturn(acl);
        when(Acl.flattenAcl(acl)).thenReturn(aclList);

        // Act & Assert
        assertDoesNotThrow(() -> {
            CUT.validateRecordAcl(recordProcessing);
        });
    }

}
