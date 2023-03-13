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
import org.opengroup.osdu.storage.validation.impl.JsonPatchOperationValidator;

import javax.validation.ConstraintValidatorContext;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JsonPatchOperationValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private ConstraintValidatorContext context;

    private JsonPatchOperationValidator sut;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        sut = new JsonPatchOperationValidator();
        ConstraintValidatorContext.ConstraintViolationBuilder builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test(expected = RequestValidationException.class)
    public void shouldThrowException_ifPatchHasMoveOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"move\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }

    @Test(expected = RequestValidationException.class)
    public void shouldThrowException_ifPatchHasCopyOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"copy\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }

    @Test(expected = RequestValidationException.class)
    public void shouldThrowException_ifPatchHasTestOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"test\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }

    @Test
    public void should_returnTrue_ifPatchHasAddOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/acl/viewers\"," +
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
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldThrowException_ifPatchHasEmptyOperations() throws IOException {
        String jsonString = "[]";

        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_SIZE);

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }

    @Test
    public void shouldThrowException_ifPatchExceedLimitOfOperations() throws IOException {
        StringBuilder jsonString = new StringBuilder("[");
        for (int i = 0; i <= JsonPatchOperationValidator.MAX_NUMBER; i++) {
            jsonString.append("{\"op\": \"add\", \"path\": \"/acl/viewers\", \"value\": \"value\"},");
        }
        jsonString.deleteCharAt(jsonString.length() - 1).append("]");
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_SIZE);

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString.toString())), context);
    }

}
