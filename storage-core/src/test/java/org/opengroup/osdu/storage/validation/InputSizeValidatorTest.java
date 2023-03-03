package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.storage.validation.impl.InputSizeValidator;

import javax.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.Silent.class)
public class InputSizeValidatorTest {
    private final String recordId = "tenant:test:record:123";
    private final int maxInputSize = 100;

    @Mock
    private ConstraintValidatorContext context;

    private InputSizeValidator sut;

    @Before
    public void setup() {
        sut = new InputSizeValidator();
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_returnTrue_ifValidInputSize() {
        RecordQuery recordQuery = new RecordQuery();
        List<String> ids = new ArrayList<>();
        for (int i = 1; i <= maxInputSize; i++) {
            ids.add(recordId + i);
        }
        recordQuery.setIds(ids);

        assertTrue(sut.isValid(recordQuery, context));
    }

    @Test(expected = RequestValidationException.class)
    public void shouldFail_ifInputSizeExceeds100() {
        RecordQuery recordQuery = new RecordQuery();
        List<String> ids = new ArrayList<>();

        for (int i = 1; i <= maxInputSize + 1; i++) {
            ids.add(recordId + i);
        }
        recordQuery.setIds(ids);

        sut.isValid(recordQuery, context);
    }

    @Test(expected = RequestValidationException.class)
    public void shouldFail_ifInputSizeIsZero() {
        RecordQuery recordQuery = new RecordQuery();
        List<String> ids = new ArrayList<>();
        recordQuery.setIds(ids);

        sut.isValid(recordQuery, context);
    }
}
