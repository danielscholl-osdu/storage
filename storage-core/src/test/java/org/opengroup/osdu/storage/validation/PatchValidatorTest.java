package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.storage.model.RecordPatchOperation;
import org.opengroup.osdu.storage.validation.impl.PatchValidator;

import javax.validation.ConstraintValidatorContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PatchValidatorTest {

    private static final String OTHERS_PATH = "/status";
    private static final String TAG_PATH = "/tags";
    private static final String ACL_VIEWERS_PATH = "/acl/viewers";
    private static final String ACL_OWNERS_PATH = "/acl/owners";
    private static final String LEGAL_PATH = "/legal/legaltags";
    private static final String KIND_PATH = "/kind";
    private static final String ANCESTRY_PATH = "/ancestry";
    private static final String DATA_PATH = "/data";

    private static final String PATCH_ADD = "add";
    private static final String PATCH_REMOVE = "remove";
    private static final String PATCH_REPLACE = "replace";
    private static final String PATCH_MOVE = "move";
    private static final String PATCH_COPY = "copy";
    private static final String PATCH_TEST = "test";

    @Mock
    private ConstraintValidatorContext context;

    private RecordPatchOperation operation;
    private PatchValidator sut;

    @Before
    public void setup() {
        sut = new PatchValidator();
        ConstraintValidatorContext.ConstraintViolationBuilder builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_PATH)).thenReturn(builder);
        when(context.buildConstraintViolationWithTemplate(ValidationDoc.INVALID_PATCH_OPERATION)).thenReturn(builder);
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_returnFalse_whenOperationIsNull() {
        assertFalse(this.sut.isValid(null, this.context));
    }

    @Test
    public void should_returnFalse_whenPathIsNull() {
        operation = buildOperation(null, "ANY");
        assertFalse(this.sut.isValid(operation, this.context));
    }

    @Test
    public void should_returnFalse_whenPathIsEmpty() {
        operation = buildOperation("", "ANY");
        assertFalse(this.sut.isValid(operation, this.context));
    }

    @Test
    public void should_returnFalse_ForOthers_AddOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnFalse_ForOthers_RemoveOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_REMOVE);
    }

    @Test
    public void should_returnFalse_ForOthers_MoveOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForOthers_CopyOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForOthers_TestOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnFalse_ForNonTag_ReplaceOp() {
        verifyIsInvalid(OTHERS_PATH, PATCH_REPLACE);
    }

    // --- Tag Path tests ---

    @Test
    public void should_returnFalse_ForTag_MoveOp() {
        verifyIsInvalid(TAG_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForTag_CopyOp() {
        verifyIsInvalid(TAG_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForTag_TestOp() {
        verifyIsInvalid(TAG_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForTag_ReplaceOp() {
        verifyIsValid(TAG_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForTag_AddOp() {
        verifyIsValid(TAG_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForTag_RemoveOp() {
        verifyIsValid(TAG_PATH, PATCH_REMOVE);
    }


    // --- Acl viewers Path tests ---
    @Test
    public void should_returnFalse_ForAclViewers_MoveOp() {
        verifyIsInvalid(ACL_VIEWERS_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForAclViewers_CopyOp() {
        verifyIsInvalid(ACL_VIEWERS_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForAclViewers_TestOp() {
        verifyIsInvalid(ACL_VIEWERS_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForAclViewers_ReplaceOp() {
        verifyIsValid(ACL_VIEWERS_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForAclViewers_AddOp() {
        verifyIsValid(ACL_VIEWERS_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForAclViewers_RemoveOp() {
        verifyIsValid(ACL_VIEWERS_PATH, PATCH_REMOVE);
    }

    // --- Acl owners Path tests ---
    @Test
    public void should_returnFalse_ForAclOwners_MoveOp() {
        verifyIsInvalid(ACL_OWNERS_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForAclOwners_CopyOp() {
        verifyIsInvalid(ACL_OWNERS_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForAclOwners_TestOp() {
        verifyIsInvalid(ACL_OWNERS_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForAclOwners_ReplaceOp() {
        verifyIsValid(ACL_OWNERS_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForAclOwners_AddOp() {
        verifyIsValid(ACL_OWNERS_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForAclOwners_RemoveOp() {
        verifyIsValid(ACL_OWNERS_PATH, PATCH_REMOVE);
    }

    // --- Legal Path tests ---
    @Test
    public void should_returnFalse_ForLegal_MoveOp() {
        verifyIsInvalid(LEGAL_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForLegal_CopyOp() {
        verifyIsInvalid(LEGAL_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForLegal_TestOp() {
        verifyIsInvalid(LEGAL_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForLegal_ReplaceOp() {
        verifyIsValid(LEGAL_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForLegal_AddOp() {
        verifyIsValid(LEGAL_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForLegal_RemoveOp() {
        verifyIsValid(LEGAL_PATH, PATCH_REMOVE);
    }

    // --- Kind Path tests ---

    @Test
    public void should_returnFalse_ForKind_MoveOp() {
        verifyIsInvalid(KIND_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForKind_CopyOp() {
        verifyIsInvalid(KIND_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForKind_TestOp() {
        verifyIsInvalid(KIND_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForKind_ReplaceOp() {
        verifyIsValid(KIND_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForKind_AddOp() {
        verifyIsValid(KIND_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForKind_RemoveOp() {
        verifyIsValid(KIND_PATH, PATCH_REMOVE);
    }

    // --- Ancestry Path tests ---

    @Test
    public void should_returnFalse_ForAncestry_MoveOp() {
        verifyIsInvalid(ANCESTRY_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForAncestry_CopyOp() {
        verifyIsInvalid(ANCESTRY_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForAncestry_TestOp() {
        verifyIsInvalid(ANCESTRY_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForAncestry_ReplaceOp() {
        verifyIsValid(ANCESTRY_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForAncestry_AddOp() {
        verifyIsValid(ANCESTRY_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForAncestry_RemoveOp() {
        verifyIsValid(ANCESTRY_PATH, PATCH_REMOVE);
    }

    // --- Data Path tests ---

    @Test
    public void should_returnFalse_ForData_MoveOp() {
        verifyIsInvalid(DATA_PATH, PATCH_MOVE);
    }

    @Test
    public void should_returnFalse_ForData_CopyOp() {
        verifyIsInvalid(DATA_PATH, PATCH_COPY);
    }

    @Test
    public void should_returnFalse_ForData_TestOp() {
        verifyIsInvalid(DATA_PATH, PATCH_TEST);
    }

    @Test
    public void should_returnTrue_ForData_ReplaceOp() {
        verifyIsValid(DATA_PATH, PATCH_REPLACE);
    }

    @Test
    public void should_returnTrue_ForData_AddOp() {
        verifyIsValid(DATA_PATH, PATCH_ADD);
    }

    @Test
    public void should_returnTrue_ForData_RemoveOp() {
        verifyIsValid(DATA_PATH, PATCH_REMOVE);
    }

    private void verifyIsValid(String path, String op) {
        operation = buildOperation(path, op);
        assertTrue(this.sut.isValid(operation, this.context));
    }

    private void verifyIsInvalid(String path, String op) {
        operation = buildOperation(path, op);
        assertFalse(this.sut.isValid(operation, this.context));
    }

    private RecordPatchOperation buildOperation(String path, String op) {
        return RecordPatchOperation.builder().op(op).path(path).build();
    }
}
