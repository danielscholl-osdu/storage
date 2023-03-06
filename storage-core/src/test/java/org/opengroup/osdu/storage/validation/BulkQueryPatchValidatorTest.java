package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.validation.impl.BulkQueryPatchValidator;

import javax.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.DUPLICATE_RECORD_ID;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PAYLOAD;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_RECORD_ID_PATCH;

@RunWith(MockitoJUnitRunner.class)
public class BulkQueryPatchValidatorTest {
    @Mock
    private ConstraintValidatorContext context;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private BulkQueryPatchValidator sut;

    @Before
    public void setup() {
        sut = new BulkQueryPatchValidator();
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_throwValidationException_ifNullInput() {
        exceptionRulesAndMethodRun(null, INVALID_PAYLOAD);
    }

    @Test
    public void should_throwValidationException_ifDuplicateRecords() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        List<String> ids = new ArrayList<>();
        ids.add("tenant:test:record:123");
        ids.add("tenant:test:record:123");
        recordQueryPatch.setIds(ids);
        exceptionRulesAndMethodRun(recordQueryPatch, DUPLICATE_RECORD_ID);
    }

    @Test
    public void should_throwValidationException_ifWrongFormatRecord() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        List<String> ids = new ArrayList<>();
        ids.add("tenant:testrecord");
        recordQueryPatch.setIds(ids);
        exceptionRulesAndMethodRun(recordQueryPatch, INVALID_RECORD_ID_PATCH);
    }

    @Test
    public void should_returnTrue_ifValidRecord() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        List<String> ids = new ArrayList<>();
        ids.add("tenant:test:record");
        recordQueryPatch.setIds(ids);
        assertTrue(sut.isValid(recordQueryPatch, context));
    }

    private void exceptionRulesAndMethodRun(RecordQueryPatch recordQueryPatch, String errorMessage) {
        exceptionRule.expect(RequestValidationException.class);
        exceptionRule.expectMessage(errorMessage);
        sut.isValid(recordQueryPatch, context);
    }
}
