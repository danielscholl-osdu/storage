// Copyright © Amazon Web Services
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.storage.provider.aws.di.EntitlementsServiceImpl;
import org.opengroup.osdu.storage.provider.aws.security.UserAccessService;
import org.opengroup.osdu.storage.provider.aws.util.CacheHelper;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class UserAccessServiceTest {

    @InjectMocks
    private UserAccessService CUT = new UserAccessService();

    Acl acl;

    @Before
    public void setUp() {
        initMocks(this);

        CacheHelper cacheHelper = Mockito.mock(CacheHelper.class);
        Mockito.when(cacheHelper.getGroupCacheKey(Mockito.anyObject())).thenReturn("test-cache-key");
        Whitebox.setInternalState(CUT, "cacheHelper", cacheHelper);

        ICache cache = Mockito.mock(ICache.class);
        Mockito.when(cache.get(Mockito.anyObject())).thenReturn(null);
        Mockito.doNothing().when(cache).put(Mockito.anyObject(), Mockito.anyObject());
        Whitebox.setInternalState(CUT, "cache", cache);

        EntitlementsServiceImpl entitlementsService = Mockito.mock(EntitlementsServiceImpl.class);
        Groups groups = new Groups();
        List<GroupInfo> groupInfos = new ArrayList<>();
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setName("data.tenant@byoc.local");
        groupInfo.setEmail("data.tenant@byoc.local");
        groupInfos.add(groupInfo);
        groups.setGroups(groupInfos);

        try {
            Mockito.when(entitlementsService.getGroups()).thenReturn(groups);
        } catch (EntitlementsException e){
            throw new RuntimeException(e);
        }

        IEntitlementsFactory factory = Mockito.mock(IEntitlementsFactory.class);
        Mockito.when(factory.create(Mockito.anyObject())).thenReturn(entitlementsService);
        Whitebox.setInternalState(CUT, "entitlementsFactory", factory);
    }

    @Test
    public void userHasAccessToRecord_authorizedUser_ReturnsTrue() {
        // Arrange
        acl = new Acl();
        String[] owners = { "data.tenant@byoc.local" };
        String[] viewers = { "data.tenant@byoc.local" };
        acl.setOwners(owners);
        acl.setViewers(viewers);

        // Act
        boolean actual = CUT.userHasAccessToRecord(acl);

        // Assert
        Assert.assertTrue(actual);
    }

    @Test
    public void userHasAccessToRecord_unauthorizedUser_ReturnsFalse() {
        // Arrange
        acl = new Acl();
        acl.setOwners(new String[] {});
        acl.setViewers(new String [] {});

        // Act
        boolean actual = CUT.userHasAccessToRecord(acl);

        // Assert
        Assert.assertFalse(actual);
    }
}
