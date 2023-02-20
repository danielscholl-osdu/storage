package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.storage.validation.impl.JsonPatchValidator;

import javax.validation.ConstraintValidatorContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JsonPatchValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    private JsonPatchValidator sut;

    @Before
    public void setup() {
        sut = new JsonPatchValidator();
        ConstraintValidatorContext.ConstraintViolationBuilder builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH)).thenReturn(builder);
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_returnFalse_ifPatchHasMoveOperation() {

    }

    @Test
    public void should_returnFalse_ifPatchHasCopyOperation() {

    }

    @Test
    public void should_returnFalse_ifPatchHasTestOperation() {

    }

    @Test
    public void should_returnTrue_ifPatchHasAddOperation() {

    }

    @Test
    public void should_returnTrue_ifPatchHasRemoveOperation() {

    }

    @Test
    public void should_returnTrue_ifPatchHasReplaceOperation() {

    }

    @Test
    public void should_returnFalse_ifPatchHasInvalidPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidAclPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidLegalPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidAncestryPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidKindPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidDataPath() {

    }

    @Test
    public void should_returnTrue_ifPatchHasValidMetaPath() {

    }
}
