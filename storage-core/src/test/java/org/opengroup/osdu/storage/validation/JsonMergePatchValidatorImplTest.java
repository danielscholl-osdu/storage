// Copyright 2017-2023, Schlumberger
// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.validation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.validation.impl.JsonMergePatchValidatorImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class JsonMergePatchValidatorImplTest {

    @Mock
    private DpsHeaders headers;
    @Mock
    private IEntitlementsAndCacheService entitlementsAndCacheService;
    @Mock
    private ILegalService legalService;

    @InjectMocks
    private JsonMergePatchValidatorImpl sut;

    @Test
    public void validateACLs_shouldThrowBadRequest_whenAclsAreInvalid() {
        Set<String> acls = new HashSet<>();
        acls.add("data.invalid@opendes.contoso.com");
        when(entitlementsAndCacheService.isValidAcl(headers, acls)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> sut.validateACLs(acls));

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, ex.getError().getCode());
        assertTrue(ex.getError().getReason().contains("Invalid ACLs"));
    }

    @Test
    public void validateACLs_shouldNotThrow_whenAclsAreValid() {
        Set<String> acls = new HashSet<>();
        acls.add("data.valid@opendes.contoso.com");
        when(entitlementsAndCacheService.isValidAcl(headers, acls)).thenReturn(true);

        assertDoesNotThrow(() -> sut.validateACLs(acls));
    }

    @Test
    public void validateACLs_shouldNotThrow_whenAclsAreEmpty() {
        Set<String> acls = Collections.emptySet();

        assertDoesNotThrow(() -> sut.validateACLs(acls));
        verify(entitlementsAndCacheService, never()).isValidAcl(any(), any());
    }

    @Test
    public void validateKind_shouldThrowRequestValidationException_whenKindIsInvalid() {
        assertThrows(RequestValidationException.class, () -> sut.validateKind("invalid-kind"));
    }

    @Test
    public void validateKind_shouldNotThrow_whenKindIsValid() {
        assertDoesNotThrow(() -> sut.validateKind("opendes:wks:doc:1.0.0"));
    }

    @Test
    public void validateLegalTags_shouldCallLegalService_whenTagsAreNotEmpty() {
        Set<String> tags = new HashSet<>();
        tags.add("opendes-public-usa-dataset-1");

        sut.validateLegalTags(tags);

        verify(legalService).validateLegalTags(tags);
    }

    @Test
    public void validateLegalTags_shouldNotCallLegalService_whenTagsAreEmpty() {
        sut.validateLegalTags(Collections.emptySet());

        verify(legalService, never()).validateLegalTags(any());
    }
}
