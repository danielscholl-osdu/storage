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

package org.opengroup.osdu.storage.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.service.IEntitlementsExtensionService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.opengroup.osdu.storage.service.IEntitlementsExtensionService.AuthorizationResult;

@ExtendWith(MockitoExtension.class)
public class AuthorizationFilterTest {

    @Mock
    private IEntitlementsExtensionService entitlementsAndCacheService;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private AuthorizationFilter sut;

    @Test
    public void testHasRole_Authorized() {
        when(entitlementsAndCacheService.authorizeWithGroupName(headers, StorageRole.ADMIN))
                .thenReturn(new AuthorizationResult("user@example.com", StorageRole.ADMIN));

        boolean result = sut.hasRole(StorageRole.ADMIN);

        assertTrue(result);
        verify(headers).put(eq(DpsHeaders.USER_EMAIL), eq("user@example.com"));
        verify(headers).put(eq(DpsHeaders.USER_AUTHORIZED_GROUP_NAME), eq(StorageRole.ADMIN));
    }

    @Test
    public void testHasRole_Unauthorized() {
        when(entitlementsAndCacheService.authorizeWithGroupName(headers, StorageRole.ADMIN))
                .thenThrow(new AppException(403, "Access denied", "The user is not authorized to perform this action"));

        assertThrows(AppException.class, () -> sut.hasRole(StorageRole.ADMIN));
    }

    @Test
    public void testHasRole_MultipleRoles_MatchesFirst() {
        when(entitlementsAndCacheService.authorizeWithGroupName(headers, StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN))
                .thenReturn(new AuthorizationResult("user@example.com", StorageRole.VIEWER));

        boolean result = sut.hasRole(StorageRole.VIEWER, StorageRole.CREATOR, StorageRole.ADMIN);

        assertTrue(result);
        verify(headers).put(eq(DpsHeaders.USER_EMAIL), eq("user@example.com"));
        verify(headers).put(eq(DpsHeaders.USER_AUTHORIZED_GROUP_NAME), eq(StorageRole.VIEWER));
    }
}
