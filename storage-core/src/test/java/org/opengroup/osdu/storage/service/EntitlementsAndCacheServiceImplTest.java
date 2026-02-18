// Copyright 2017-2019, Schlumberger
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

package org.opengroup.osdu.storage.service;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import org.opengroup.osdu.storage.service.IEntitlementsExtensionService.AuthorizationResult;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EntitlementsAndCacheServiceImplTest {

    private static final String MEMBER_EMAIL = "tester@gmail.com";
    private static final String MEMBER_EMAIL_2 = "tester2@gmail.com";
    private static final String HEADER_ACCOUNT_ID = "anyTenant";
    private static final String HEADER_AUTHORIZATION = "anyCrazyToken";
    private static final String USER_ID = "userID";

    private static final String USER_ID_2 = "userID2";

    @Mock
    private IEntitlementsFactory entitlementFactory;

    @Mock
    private ICache<String, Groups> cache;

    private DpsHeaders headers;

    @Mock
    private IEntitlementsService entitlementService;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private DpsHeaders dpsHeaders;

    @Mock
    private EntitlementsAndCacheServiceImpl entitlementsAndCacheService;

    @InjectMocks
    private EntitlementsAndCacheServiceImpl sut;

    private static final Map<String, String> headerMap = new HashMap<>();

    @BeforeEach
    public void setup() {

        setDefaultHeaders();

        this.headers = DpsHeaders.createFromMap(headerMap);

        lenient().when(this.entitlementFactory.create(this.headers)).thenReturn(this.entitlementService);
    }

    private void setDefaultHeaders() {
        headerMap.put(DpsHeaders.ACCOUNT_ID, HEADER_ACCOUNT_ID);
        headerMap.put(DpsHeaders.AUTHORIZATION, HEADER_AUTHORIZATION);
        headerMap.put(DpsHeaders.USER_ID, USER_ID);
    }

    @Test
    public void should_returnMemberEmail_when_authorizationIsSuccessfull() throws Exception {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@gmail.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        assertEquals(MEMBER_EMAIL, this.sut.authorize(this.headers, "role2"));
    }

    @Test
    public void should_returnHttp403_when_userDoesNotBelongToRoleGroup() throws EntitlementsException {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@gmail.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        try {
            this.sut.authorize(this.headers, "role3");

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwAppException_when_entitlementExceptionHappens() throws EntitlementsException {

        final String ERROR_MSG = "FATAL ERROR";

        HttpResponse response = mock(HttpResponse.class);
        when(response.getResponseCode()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        when(response.getBody()).thenReturn("{\"code\":500,\"reason\":\"Access denied\",\"message\":\"The user is not authorized to perform this action\"}");

        EntitlementsException expectedException = new EntitlementsException(ERROR_MSG, response);

        when(this.entitlementService.getGroups()).thenThrow(expectedException);

        try {
            this.sut.authorize(this.headers, "role3");

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("The user is not authorized to perform this action", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_throwAppExceptionAndPropagateMessage_when_entitlementExceptionHappens() throws EntitlementsException {

        final String ERROR_MSG = "FATAL ERROR";

        HttpResponse response = mock(HttpResponse.class);
        when(response.getResponseCode()).thenReturn(HttpStatus.SC_FORBIDDEN);
        when(response.getBody()).thenReturn("{\"code\":403,\n" +
                "\"reason\":\"Access denied\",\"message\":\"Invalid data partition id\"}");

        EntitlementsException expectedException = new EntitlementsException(ERROR_MSG, response);

        when(this.entitlementService.getGroups()).thenThrow(expectedException);

        try {
            this.sut.authorize(this.headers, "role3");

            fail("Should not succeed");
        } catch (AppException e) {
            assertEquals(HttpStatus.SC_FORBIDDEN, e.getError().getCode());
            assertEquals("Access denied", e.getError().getReason());
            assertEquals("Invalid data partition id", e.getError().getMessage());
        } catch (Exception e) {
            fail("Should not get different exception");
        }
    }

    @Test
    public void should_getGroupsFromCache_when_requestHashIsFoundInCache() throws EntitlementsException {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@gmail.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        // First call, getting groups from entitlements
        assertEquals(MEMBER_EMAIL, this.sut.authorize(this.headers, "role2"));

        when(this.cache.get("5QjU5g==")).thenReturn(groups);

        // Second call, getting groups from cache
        assertEquals(MEMBER_EMAIL, this.sut.authorize(this.headers, "role2"));
        verify(this.entitlementService, times(1)).getGroups();
        verify(this.cache, times(2)).get("5QjU5g==");
        verify(this.cache, times(1)).put("5QjU5g==", groups);
    }

    @Test
    public void should_getGroupsFromCache_when_requestHashIsFoundInCacheTwoDifferentUSerIDs() throws EntitlementsException {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@gmail.com");
        g2.setName("role2");

        GroupInfo g3 = new GroupInfo();
        g3.setEmail("role3@gmail.com");
        g3.setName("role3");

        GroupInfo g4 = new GroupInfo();
        g4.setEmail("role4@gmail.com");
        g4.setName("role4");

        List<GroupInfo> groupsInfo1 = new ArrayList<>();
        groupsInfo1.add(g1);
        groupsInfo1.add(g2);

        List<GroupInfo> groupsInfo2 = new ArrayList<>();
        groupsInfo2.add(g3);
        groupsInfo2.add(g4);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo1);
        groups.setDesId(MEMBER_EMAIL);

        Groups groups2 = new Groups();
        groups2.setGroups(groupsInfo2);
        groups2.setDesId(MEMBER_EMAIL_2);

        when(this.cache.get("5QjU5g==")).thenReturn(groups);
        when(this.cache.get("RtEtMQ==")).thenReturn(groups2);

        assertEquals(MEMBER_EMAIL, this.sut.authorize(this.headers, "role2"));

        verify(this.cache, times(1)).get("5QjU5g==");

        headerMap.put(DpsHeaders.USER_ID, USER_ID_2);
        this.headers = DpsHeaders.createFromMap(headerMap);

        assertEquals(MEMBER_EMAIL_2, this.sut.authorize(this.headers, "role3"));

        // Verifies that cache key is different in case of different user
        verify(this.cache, times(1)).get("RtEtMQ==");

    }

    @Test
    public void should_returnTure_when_requesterIsDataManager() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("users.data.root@tenant.gmail.com");
        g1.setName("users.data.root");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        assertTrue(this.sut.isDataManager(this.headers));
    }

    @Test
    public void should_returnFalse_when_requesterIsNotDataManager() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("users@tenant.gmail.com");
        g1.setName("users");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        assertFalse(this.sut.isDataManager(this.headers));
    }

    @Test
    public void should_returnTrue_when_AclIsValid() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@tenant.gmail.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");
        acls.add("valid2@tenant.gmail.com");

        assertEquals(true, this.sut.isValidAcl(this.headers, acls));
    }

    @Test
    public void should_returnFalse_when_AclDoesNotMatchEmailDomain() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@tenant.gmail.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");
        acls.add("invalid@test.whatever.com");

        assertEquals(false, this.sut.isValidAcl(this.headers, acls));
    }

    @Test
    public void should_returnFalse_when_AclNull()
            throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("test.tenant@test.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add(null);

        assertFalse(this.sut.isValidAcl(this.headers, acls));
    }

    @Test
    public void should_returnFalse_when_AclNoAtSymbol()
            throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("test.tenant@test.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("data.default.ownersopendes.test.com");
        assertFalse(this.sut.isValidAcl(this.headers, acls));
    }

    @Test
    public void should_returnFalse_when_NotInOwnerList() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@tenant.slb.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        String[] ownerList = new String[]{"owner1@tenant.slb.com", "owner2@tenant.slb.com"};

        assertEquals(false, this.sut.hasOwnerAccess(this.headers, ownerList));
    }

    @Test
    public void should_returnTrue_when_UserInOwnerList() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@tenant.slb.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        String[] ownerList = new String[]{"role1@tenant.slb.com", "owner2@tenant.slb.com"};

        assertEquals(true, this.sut.hasOwnerAccess(this.headers, ownerList));
    }

    @Test
    public void should_throwAppException_when_NoGroupGotFromCacheOrEntitlements() throws EntitlementsException {
        List<GroupInfo> groupsInfo = new ArrayList<>();
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");

        assertThrows(AppException.class, ()->{
            this.sut.isValidAcl(this.headers, acls);
        });
    }

    @Test
    public void should_throwAppException_when_EmailOfGroupNotMatchingValidRegex_NoAtSymbol()
            throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("test.tenant.gmail.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");

        assertThrows(AppException.class, ()->{
            this.sut.isValidAcl(this.headers, acls);
        });
    }

    @Test
    public void should_throwAppException_when_EmailOfGroupNotMatchingValidRegex_NoGroupName()
            throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("@tenant.gmail.com");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");

        assertThrows( AppException.class, ()->{
            this.sut.isValidAcl(this.headers, acls);
        });
    }

    @Test
    public void should_throwAppException_when_EmailOfGroupNotMatchingValidRegex_DomainTooSimple()
            throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("test@tenantgmailcom");
        g1.setName("role1");
        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        when(this.entitlementService.getGroups()).thenReturn(groups);

        Set<String> acls = new HashSet<>();
        acls.add("valid@tenant.gmail.com");

        assertThrows(AppException.class, ()->{
            this.sut.isValidAcl(this.headers, acls);
        });
    }

    @Test
    public void should_returnTrue_when_aclContainedInGroups() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@slb.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@slb.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        String[] viewers = new String[]{"role1@slb.com"};
        String[] owners = new String[]{"role2@slb.com"};
        Acl storageAcl = new Acl();
        storageAcl.setOwners(owners);
        storageAcl.setViewers(viewers);

        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setAcl(storageAcl);
        recordMetadata.setId("acl-check-1");

        List<RecordMetadata> input = new ArrayList<>();
        input.add(recordMetadata);

        List<RecordMetadata> result = this.sut.hasValidAccess(input, this.headers);
        assertEquals(1, result.size());
        assertEquals("acl-check-1", result.get(0).getId());
    }

    @Test
    public void should_returnTrue_when_aclNotContainedInGroups() throws EntitlementsException {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@slb.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@slb.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        String[] viewers = new String[]{"role3@slb.com"};
        String[] owners = new String[]{"role4@slb.com"};
        Acl storageAcl = new Acl();
        storageAcl.setOwners(owners);
        storageAcl.setViewers(viewers);

        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setAcl(storageAcl);
        recordMetadata.setId("acl-check-2");

        List<RecordMetadata> input = new ArrayList<>();
        input.add(recordMetadata);

        List<RecordMetadata> result = this.sut.hasValidAccess(input, this.headers);
        assertEquals(0, result.size());
    }


    @Test
    public void shouldCheckValidAccess_when_aclIsNull() throws EntitlementsException {

        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@slb.com");
        g1.setName("role1");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId("acl-check-2");

        List<RecordMetadata> input = new ArrayList<>();
        input.add(recordMetadata);

        List<RecordMetadata> result = this.sut.hasValidAccess(input, this.headers);
        assertTrue(result.isEmpty());
    }

    @Test
    public void should_returnAuthorizationResult_when_authorizeWithGroupName() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        GroupInfo g2 = new GroupInfo();
        g2.setEmail("role2@gmail.com");
        g2.setName("role2");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);
        groupsInfo.add(g2);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        AuthorizationResult result = this.sut.authorizeWithGroupName(this.headers, "role2");
        assertEquals(MEMBER_EMAIL, result.user());
        assertEquals("role2", result.userAuthorizedGroupName());
    }

    @Test
    public void should_throwAppException_when_authorizeWithGroupNameUnauthorized() throws EntitlementsException {
        GroupInfo g1 = new GroupInfo();
        g1.setEmail("role1@gmail.com");
        g1.setName("role1");

        List<GroupInfo> groupsInfo = new ArrayList<>();
        groupsInfo.add(g1);

        Groups groups = new Groups();
        groups.setGroups(groupsInfo);
        groups.setDesId(MEMBER_EMAIL);

        when(this.entitlementService.getGroups()).thenReturn(groups);

        AppException exception = assertThrows(AppException.class,
                () -> this.sut.authorizeWithGroupName(this.headers, "role3"));
        assertEquals(403, exception.getError().getCode());
    }
}
