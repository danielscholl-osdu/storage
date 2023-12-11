package org.opengroup.osdu.storage.provider.azure;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsFactory;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsService;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EntitlementsAndCacheServiceAzureTest {

    private static final String expectedErrorMessage = "Unknown error happened when validating ACL";
    @InjectMocks
    EntitlementsAndCacheServiceAzure entitlementsAndCacheServiceAzure;
    @Mock
    private ICache<String, Groups> cache;
    @Mock
    private DpsHeaders headers;
    @Mock
    private IEntitlementsFactory entitlementsFactory;
    @Mock
    private IEntitlementsService entitlementsService;
    @Mock
    private JaxRsDpsLog logger;

    @Test
    public void hasAccessToDataReturnsTrue_when_RequiredGroupsArePresent() {
        Mockito.when(cache.get(Mockito.any())).thenReturn(createRandomGroup());
        Set<String> acls = new HashSet<>();
        acls.add("service.service_name2.user@blabla.com");

        boolean result = entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls);

        assertTrue(result);
    }

    @Test
    public void hasAccessToDataThrowsAppException_when_noRequiredGroupsArePresent() throws EntitlementsException {
        Mockito.when(cache.get(Mockito.any())).thenReturn(new Groups());
        Set<String> acls = new HashSet<>();

        AppException appException = assertThrows(AppException.class, () -> entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, appException.getError().getCode());
        assertEquals(expectedErrorMessage, appException.getError().getMessage());
    }

    @Test
    public void hasAccessToDataThrowsAppException_when_emailIdValidationFails() {
        Groups groups = createRandomGroup();
        groups.getGroups().get(0).setEmail("invalid_email_without_any_dot_coms");
        Mockito.when(cache.get(Mockito.any())).thenReturn(groups);
        Set<String> acls = new HashSet<>();

        AppException appException = assertThrows(AppException.class, () -> entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls));

        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, appException.getError().getCode());
        assertEquals(expectedErrorMessage, appException.getError().getMessage());
    }

    @Test
    public void hasAccessToDataReturnsFalse_when_noAclsPresent() {
        Groups groups = createRandomGroup();
        Mockito.when(cache.get(Mockito.any())).thenReturn(groups);
        Set<String> acls = new HashSet<>();

        boolean bAccessPresent = entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls);

        assertFalse(bAccessPresent);
    }

    @Test
    public void hasAccessToDataReturnsFalse_when_noAclTenantsDontMatch() {
        Groups groups = createRandomGroup();
        Mockito.when(cache.get(Mockito.any())).thenReturn(groups);
        Set<String> acls = new HashSet<>();
        acls.add("group_not_present_in_groups@different_domain.com");

        boolean bAccessPresent = entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls);

        assertFalse(bAccessPresent);
    }

    @Test
    public void hasAccessToDataReturnsFalse_when_noAclRolesDontMatch() {
        Groups groups = createRandomGroup();
        Mockito.when(cache.get(Mockito.any())).thenReturn(groups);
        Set<String> acls = new HashSet<>();
        acls.add("group_not_present_in_groups@blabla.com");

        boolean bAccessPresent = entitlementsAndCacheServiceAzure.hasAccessToData(headers, acls);

        assertFalse(bAccessPresent);
    }

    private Groups createRandomGroup() {
        Groups groups = new Groups();

        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setEmail("service.service_name.user@blabla.com");
        groupInfo.setName("service.service_name.user");
        groupInfo.setDescription("description");

        GroupInfo groupInfo2 = new GroupInfo();
        groupInfo2.setEmail("service.service_name2.user@blabla.com");
        groupInfo2.setName("service.service_name2.user");
        groupInfo2.setDescription("description");

        groups.setDesId("username@blabla.com");
        groups.setMemberEmail("username@blabla.com");
        groups.setGroups(Arrays.asList(groupInfo, groupInfo2));
        return groups;
    }
}
