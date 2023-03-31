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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.storage.validation.impl.JsonPatchValidator;

import javax.validation.ConstraintValidatorContext;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.opengroup.osdu.storage.util.StringConstants.MAX_OP_NUMBER;

@RunWith(MockitoJUnitRunner.class)
public class JsonPatchValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private ConstraintValidatorContext context;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private JsonPatchValidator sut;

    @Before
    public void setup() {
        sut = new JsonPatchValidator();
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void shouldThrowException_ifPatchHasEmptyOperations() throws IOException {
        String jsonString = "[]";

        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION_SIZE);
    }

    @Test
    public void shouldThrowException_ifPatchExceedLimitOfOperations() throws IOException {
        StringBuilder jsonString = new StringBuilder("[");
        for (int i = 0; i <= MAX_OP_NUMBER; i++) {
            jsonString.append("{\"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"value\"},");
        }
        jsonString.deleteCharAt(jsonString.length() - 1).append("]");
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_SIZE);

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString.toString())), context);
    }

    @Test
    public void shouldThrowException_ifPatchHasMoveOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"move\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION);
    }

    @Test
    public void shouldThrowException_ifPatchHasCopyOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"copy\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION);
    }

    @Test
    public void shouldThrowException_ifPatchHasTestOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"test\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": \"some_value\"" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION);
    }

    @Test
    public void should_returnTrue_ifPatchHasAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/acl/viewers/-\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasRemoveOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"remove\"," +
                "             \"path\": \"/acl/viewers/1\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasReplaceOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"replace\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": [\"some_value\"]" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasMultipleArrayValuesForAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": [\"value1\", \"value2\"]" +
                "            }]";
        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldThrowException_ifPatchPathContainKeyAndInvalidPatchValueForAddTagOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/tags/hello\"," +
                "             \"value\": {\"key\" : \"value\"}" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_TAGS);
    }

    @Test
    public void shouldThrowException_ifPatchPathDoesNotContainKeyAndInvalidPatchValueForAddTagOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/tags\"," +
                "             \"value\": \"value\"" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_TAGS);
    }

    @Test
    public void should_returnTrue_ifPatchPathContainsKeyAndValidPatchValueForAddTagOperation() throws IOException {
        String jsonString = "[{\"op\": \"add\",  \"path\": \"/tags/key\", \"value\": \"value\"}]";
        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchPathDoesNotContainKeyAndValidPatchValueForAddTagOperation() throws IOException {
        String jsonString = "[{\"op\": \"add\",  \"path\": \"/tags\", \"value\": {\"key\" : \"value\"} }]";
        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasKeyValueValueForAddOperationForDataPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/data\"," +
                "             \"value\": {\"key\" : \"value\"}" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldThrowException_ifPatchHasMultipleValuesForReplaceOperationWithSpecifiedIndexInPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"replace\"," +
                "             \"path\": \"/acl/viewers/1\"," +
                "             \"value\": [\"value1\", \"value2\"]" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_SINGLE_VALUE_FORMAT_FOR_ACL_LEGAL_ANCESTRY);
    }

    @Test
    public void shouldThrowException_ifPatchHasMultipleValuesForReplaceOperationWithSpecifiedEndOfArrayInPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"replace\"," +
                "             \"path\": \"/acl/viewers/-\"," +
                "             \"value\": [\"value1\", \"value2\"]" +
                "            }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_SINGLE_VALUE_FORMAT_FOR_ACL_LEGAL_ANCESTRY);
    }

    @Test(expected = RequestValidationException.class)
    public void should_returnFalse_ifPatchHasInvalidPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/invalid_path\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }

    @Test
    public void should_returnTrue_ifPatchHasValidAclPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/acl/viewers/0\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_fail_ifPatchHasInvalidAclValueForAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_ACL_LEGAL_ANCESTRY);
    }

    @Test
    public void should_returnTrue_ifPatchHasValidLegalPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/legal/legaltags/-\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_fail_ifPatchHasInvalidValueForAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/legal/legaltags\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_ACL_LEGAL_ANCESTRY);
    }

    @Test
    public void should_fail_ifPatchHasInvalidAncestryValueForAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/ancestry/parents\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_ACL_LEGAL_ANCESTRY);
    }

    @Test
    public void should_returnTrue_ifPatchHasValidKindPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"replace\"," +
                "             \"path\": \"/kind\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidDataPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/data\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidMetaPath() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/meta\", \"value\": [\"some_value\"] }]";
        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveLegalTagsOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/legal/legaltags\" }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_PATH_FOR_REMOVE_OPERATION);
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveAclViewersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/viewers\" }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_PATH_FOR_REMOVE_OPERATION);
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveAclOwnersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/owners\" }]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_PATH_FOR_REMOVE_OPERATION);
    }

    @Test
    public void should_returnTrue_ifPatchHasValidPathForRemoveLegalTagsOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/legal/legaltags/1\" }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidPathForRemoveAclViewersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/viewers/0\" }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidPathForRemoveAclOwnersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/owners/2\" }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldFail_whenForReplaceKindValuesPresentedAsArray() throws IOException {
        String jsonString = "[{ \"op\": \"replace\", \"path\": \"/kind\", \"value\": [\"kindValue\"]}]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUES_FORMAT_FOR_KIND);
    }

    @Test
    public void shouldFail_onInvalidKindOperationRemove() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/kind\", \"value\": \"kindValue\"}]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND);
    }

    @Test
    public void shouldFail_onInvalidKindOperationAdd() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/kind\", \"value\": \"kindValue\"}]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_OPERATION_TYPE_FOR_KIND);
    }

    @Test
    public void shouldFail_onInvalidValueForMetaOperationAdd() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/meta\", \"value\": \"metaValue\"}]";
        exceptionRulesAndMethodRun(jsonString, ValidationDoc.INVALID_PATCH_VALUE_FORMAT_FOR_META);
    }

    @Test
    public void shoul_returnTrue_onValidValueForMetaOperationAdd() throws IOException {
        String jsonString = "[{ \"op\": \"add\", \"path\": \"/meta\", \"value\": [{\"key\" : \"value\"}] }]";
        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    private void exceptionRulesAndMethodRun(String jsonString, String message) throws IOException {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(message);

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }
}
