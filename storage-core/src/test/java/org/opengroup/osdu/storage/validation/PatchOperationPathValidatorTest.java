package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.storage.validation.impl.PatchOperationPathValidator;

import javax.validation.ConstraintValidatorContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PatchOperationPathValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    private PatchOperationPathValidator sut;

    @Before
    public void setup() {
        sut = new PatchOperationPathValidator();
        ConstraintValidatorContext.ConstraintViolationBuilder builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH)).thenReturn(builder);
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_returnFalse_ifInvalidPaths() {
        String invalidViewer = "/acl/viewer";
        assertFalse(this.sut.isValid(invalidViewer, this.context));

        String invalidOwner = "/acl/owner";
        assertFalse(this.sut.isValid(invalidOwner, this.context));

        String invalidOwnerAcl = "/invalid/owners";
        assertFalse(this.sut.isValid(invalidOwnerAcl, this.context));

        String invalidAclExtension = "/acl/viewers/";
        assertFalse(this.sut.isValid(invalidAclExtension, this.context));

        String invalidTags = "/tags/123";
        assertFalse(this.sut.isValid(invalidTags, this.context));

        String invalidAncestry = "/ancestry";
        assertFalse(this.sut.isValid(invalidAncestry, this.context));
    }

    @Test
    public void should_returnTrue_ifValidPaths() {
        String viewersAcl = "/acl/viewers";
        assertTrue(this.sut.isValid(viewersAcl, this.context));

        String ownersAcl = "/acl/owners";
        assertTrue(this.sut.isValid(ownersAcl, this.context));

        String legalTags = "/legal/legaltags";
        assertTrue(this.sut.isValid(legalTags, this.context));

        String tags = "/tags";
        assertTrue(this.sut.isValid(tags, this.context));

        String kind = "/kind";
        assertTrue(this.sut.isValid(kind, this.context));

        String ancestry = "/ancestry/parents";
        assertTrue(this.sut.isValid(ancestry, this.context));

        String data1 = "/data";
        assertTrue(this.sut.isValid(data1, this.context));

        String data2 = "/data/anything";
        assertTrue(this.sut.isValid(data2, this.context));

        String data3 = "/data/property/someProperty";
        assertTrue(this.sut.isValid(data3, this.context));
    }

}
