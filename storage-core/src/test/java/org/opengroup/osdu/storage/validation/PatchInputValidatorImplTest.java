// Copyright 2017-2023, Schlumberger
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.legal.ILegalService;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.service.EntitlementsAndCacheServiceImpl;
import org.opengroup.osdu.storage.validation.impl.PatchInputValidatorImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PARENT_RECORD_ID_FORMAT;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PARENT_RECORD_VERSION_FORMAT;
import static org.opengroup.osdu.storage.util.TestUtils.buildAppExceptionMatcher;
import static org.opengroup.osdu.storage.validation.ValidationDoc.KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION;

@RunWith(MockitoJUnitRunner.class)
public class PatchInputValidatorImplTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private EntitlementsAndCacheServiceImpl entitlementsAndCacheService;

    @Mock
    private ILegalService legalService;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private PatchInputValidatorImpl sut;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test(expected = AppException.class)
    public void shouldThrowException_ifPatchHasDuplicates() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"some_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"some_value\"}" +
                "]";
        sut.validateDuplicates(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldNotThrowException_ifPatchDoesNotHaveDuplicates() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"some_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"another_value\"}" +
                "]";
        sut.validateDuplicates(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_onInvalidAcl() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"first_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/acl/owners\", \"value\": \"another_value\"}" +
                "]";
        Set<String> valueSet = new HashSet<>(Arrays.asList("first_value", "another_value"));

        when(entitlementsAndCacheService.isValidAcl(headers, valueSet)).thenReturn(false);

        exceptionRule.expect(AppException.class);
        exceptionRule.expect(buildAppExceptionMatcher("Invalid ACLs provided in acl path.", "Invalid ACLs"));

        sut.validateAcls(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldNotFail_onValidAcl() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"first_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/acl/owners\", \"value\": \"another_value\"}" +
                "]";
        Set<String> valueSet = new HashSet<>(Arrays.asList("first_value", "another_value"));

        when(entitlementsAndCacheService.isValidAcl(headers, valueSet)).thenReturn(true);

        sut.validateAcls(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(entitlementsAndCacheService).isValidAcl(headers, valueSet);
    }

    @Test
    public void shouldNotFail_onValidAcl_whenAclsArePresentedAsArray() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": [\"acl1\", \"acl2\"]}]";
        Set<String> valueSet = new HashSet<>(Arrays.asList("acl1", "acl2"));

        when(entitlementsAndCacheService.isValidAcl(headers, valueSet)).thenReturn(true);

        sut.validateAcls(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(entitlementsAndCacheService).isValidAcl(headers, valueSet);
    }

    @Test
    public void shouldValidateValuesOnlyForAclPath() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"first_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/legal/legaltags\", \"value\": \"another_value\"}" +
                "]";
        Set<String> valueSet = new HashSet<>(Collections.singletonList("first_value"));

        when(entitlementsAndCacheService.isValidAcl(headers, valueSet)).thenReturn(true);

        sut.validateAcls(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(entitlementsAndCacheService).isValidAcl(headers, valueSet);
    }

    @Test
    public void shouldValidateTagsOnlyForLegalTagPath() throws IOException {
        String jsonString = "[" +
                "{ \"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"first_value\"}," +
                "{ \"op\": \"add\", \"path\": \"/legal/legaltags\", \"value\": \"another_value\"}" +
                "]";
        Set<String> valueSet = new HashSet<>(Collections.singletonList("another_value"));

        doNothing().when(legalService).validateLegalTags(valueSet);

        sut.validateLegalTags(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(legalService).validateLegalTags(valueSet);
    }

    @Test
    public void shouldValidateLegalTags_forRemoveOperation_whenValueIsAbsent() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/legal/legaltags/0\"}]";

        sut.validateLegalTags(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(legalService, never()).validateLegalTags(anySet());
    }

    @Test
    public void shouldValidateAcls_forRemoveOperation_whenValueIsAbsent() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/viewers/0\"}]";

        sut.validateLegalTags(JsonPatch.fromJson(mapper.readTree(jsonString)));
        verify(entitlementsAndCacheService, never()).isValidAcl(eq(headers), anySet());
    }

    @Test
    public void shouldFail_onInvalidKindOperationAdd() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/kind\", \"value\": \"kindValue\"}]";

        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND);

        sut.validateKind(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_onInvalidKindOperationRemove() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/kind\", \"value\": \"kindValue\"}]";

        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND);

        sut.validateKind(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_whenValuesPresentedAsArray() throws IOException {
        String jsonString = "[{ \"op\": \"replace\", \"path\": \"/kind\", \"value\": [\"kindValue\"]}]";

        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_KIND);

        sut.validateKind(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_whenKindDoesNotFollowNamingConvention() throws IOException {
        String jsonString = "[{ \"op\": \"replace\", \"path\": \"/kind\", \"value\": \"kindValue\"}]";

        exceptionRule.expect(RequestValidationException.class);
        String message = String.format(KIND_DOES_NOT_FOLLOW_THE_REQUIRED_NAMING_CONVENTION, "kindValue");
        exceptionRule.expectMessage(message);

        sut.validateKind(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldNotFail_onValidPatchKindOperation() throws IOException {
        String jsonString = "[{ \"op\": \"replace\", \"path\": \"/kind\", \"value\": \"opendes:test:test:01.01.01\"}]";

        sut.validateKind(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_whenAncestryParentsDoesNotFollowBaseNamingConvention() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/ancestry/parents\", \"value\": \"invalidValue\"}]";

        exceptionRule.expect(RequestValidationException.class);
        String message = String.format(INVALID_PARENT_RECORD_ID_FORMAT, "invalidValue");
        exceptionRule.expectMessage(message);

        sut.validateAncestry(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldFail_whenAncestryParentsDoesNotFollowVersionNamingConvention() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/ancestry/parents\", \"value\": \"opendes:test:test:invalidVersion\"}]";

        exceptionRule.expect(RequestValidationException.class);
        String message = String.format(INVALID_PARENT_RECORD_VERSION_FORMAT, "opendes:test:test:invalidVersion");
        exceptionRule.expectMessage(message);

        sut.validateAncestry(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

    @Test
    public void shouldNotFail_onValidPatchAncestryOperation() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/ancestry/parents\", \"value\": \"opendes:test:test:12345678\"}]";

        sut.validateAncestry(JsonPatch.fromJson(mapper.readTree(jsonString)));
    }

}
