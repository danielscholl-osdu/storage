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
import org.opengroup.osdu.storage.validation.impl.JsonPatchPathValidator;

import javax.validation.ConstraintValidatorContext;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class JsonPatchPathValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private ConstraintValidatorContext context;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private JsonPatchPathValidator sut;

    @Before
    public void setup() {
        sut = new JsonPatchPathValidator();
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
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
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidLegalPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/legal/legaltags\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidAncestryPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/ancestry/parents\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnTrue_ifPatchHasValidKindPath() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
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
        String jsonString = "[{" +
                "             \"op\": \"add\"," +
                "             \"path\": \"/meta\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertTrue(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveLegalTagsOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/legal/legaltags\" }]";
        exceptionRulesAndMethodRun(jsonString);
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveAclViewersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/viewers\" }]";
        exceptionRulesAndMethodRun(jsonString);
    }

    @Test
    public void shouldThrowException_ifPatchHasInValidPathForRemoveAclOwnersOperation() throws IOException {
        String jsonString = "[{ \"op\": \"remove\", \"path\": \"/acl/owners\" }]";
        exceptionRulesAndMethodRun(jsonString);
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

    private void exceptionRulesAndMethodRun(String jsonString) throws IOException {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(ValidationDoc.INVALID_PATCH_OPERATION_PATH_FOR_REMOVE_OPERATION);

        sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context);
    }
}
