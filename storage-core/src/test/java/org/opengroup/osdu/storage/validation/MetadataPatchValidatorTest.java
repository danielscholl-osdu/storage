// Copyright 2017-2021, Schlumberger
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.entitlements.IEntitlementsAndCacheService;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PatchOperation;
import org.opengroup.osdu.storage.validation.impl.MetadataPatchValidator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MetadataPatchValidatorTest {

    private static final String PATH = "/path";
    private static final String PATH_ACL = "/acl";
    private static final String PATH_LEGAL = "/legal";
    private static final String PATH_TAGS = "/tags";

    private static final String[] INVALID_TAGS = {"tagkeytagvalue"};
    private static final String[] VALID_TAGS = {"tagkey:tagvalue"};

    private static final String[] VALUES = {"value"};

    @Mock
    private ILegalService legalService;
    @Mock
    private IEntitlementsAndCacheService entitlementsAndCacheService;
    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private MetadataPatchValidator validator;

    @Test
    public void shouldFail_onDuplicatedPath() {
        PatchOperation patchOperation = buildPatchOperation(PATH, VALUES);
        PatchOperation duplicatedPatchOperation = buildPatchOperation(PATH, VALUES);

        List<PatchOperation> operations = Arrays.asList(patchOperation, duplicatedPatchOperation);

        AppException exception = assertThrows(AppException.class, ()->{
            validator.validateDuplicates(operations);
        });
        assertEquals("Users can only update a path once per request.", exception.getMessage());
        assertEquals("Duplicate paths", exception.getError().getReason());
    }

    @Test
    public void shouldFail_onInvalidAcl() {
        PatchOperation patchOperation = buildPatchOperation(PATH_ACL, VALUES);

        when(entitlementsAndCacheService.isValidAcl(headers, new HashSet<>(Arrays.asList(VALUES)))).thenReturn(false);

        AppException exception = assertThrows(AppException.class, ()->{
            validator.validateAcls(singletonList(patchOperation));
        });
        assertEquals("Invalid ACLs provided in acl path.", exception.getMessage());
        assertEquals("Invalid ACLs", exception.getError().getReason());
    }

    @Test
    public void shouldFail_onInvalidLegal() {
        PatchOperation patchOperation = buildPatchOperation(PATH_LEGAL, VALUES);

        doThrow(new RuntimeException()).when(legalService).validateLegalTags(new HashSet<>(Arrays.asList(VALUES)));

        RuntimeException exception = assertThrows(RuntimeException.class, ()->{
            validator.validateLegalTags(singletonList(patchOperation));
        });
    }

    @Test
    public void shouldFail_onInvalidTagFormat() {
        PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, INVALID_TAGS);

        AppException exception = assertThrows(AppException.class, ()->{
            validator.validateTags(singletonList(patchOperation));
        });
        assertEquals("Invalid tags values provided", exception.getMessage());
        assertEquals("Invalid tags", exception.getError().getReason());

    }

    @Test
    public void shouldPass_onValidTagFormat() {
        PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, VALID_TAGS);

        validator.validateTags(singletonList(patchOperation));

        verifyNoMoreInteractions(legalService, entitlementsAndCacheService, headers);
    }

    @Test
    public void shouldPassForTag_whenOperationIsRemove() {
        PatchOperation patchOperation = buildPatchOperation(PATH_TAGS, INVALID_TAGS);
        patchOperation.setOp("remove");

        validator.validateTags(singletonList(patchOperation));

        verifyNoMoreInteractions(legalService, entitlementsAndCacheService, headers);
    }

    private PatchOperation buildPatchOperation(String path, String[] value) {
        return PatchOperation.builder().path(path).value(value).build();
    }
}
