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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opengroup.osdu.storage.util.TestUtils.buildAppExceptionMatcher;

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

}
