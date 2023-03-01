package org.opengroup.osdu.storage.validation;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.storage.validation.impl.InputSizeValidator;

import javax.validation.ConstraintValidatorContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.Silent.class)
public class InputSizeValidatorTest {
    private final String recordId = "tenant:test:record:123";
    private final int max_input_size = 100;

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
        for (int i = 1; i <= max_input_size; i++) {
            ids.add(recordId + i);
        }
        recordQuery.setIds(ids);

        assertTrue(sut.isValid(recordQuery, context));
    }

    @Test(expected = RequestValidationException.class)
    public void shouldFail_ifInvalidInputSize() {
        RecordQuery recordQuery = new RecordQuery();
        List<String> ids = new ArrayList<>();

        for (int i = 1; i <= max_input_size + 1; i++) {
            ids.add(recordId + i);
        }
        recordQuery.setIds(ids);

        sut.isValid(recordQuery, context);
    }
}
