package org.opengroup.osdu.storage.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.storage.validation.impl.JsonPatchOperationValidator;

import javax.validation.ConstraintValidatorContext;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class JsonPatchOperationValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    private ConstraintValidatorContext context;

    private JsonPatchOperationValidator sut;

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

    @Test
    public void should_returnFalse_ifPatchHasMoveOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"move\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";

        assertFalse(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnFalse_ifPatchHasCopyOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"copy\"," +
                "             \"from\": \"/acl/viewers\"," +
                "             \"path\": \"/acl/owners\"" +
                "            }]";

        assertFalse(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
    }

    @Test
    public void should_returnFalse_ifPatchHasTestOperation() throws IOException {
        String jsonString = "[{" +
                "             \"op\": \"test\"," +
                "             \"path\": \"/acl/viewers\"," +
                "             \"value\": \"some_value\"" +
                "            }]";

        assertFalse(sut.isValid(JsonPatch.fromJson(mapper.readTree(jsonString)), context));
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

}
