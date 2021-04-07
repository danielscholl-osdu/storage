package org.opengroup.osdu.storage.provider.azure.util;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class RecordIdValidatorTest {
    private static final String RECORD_ID_WITH_101_SYMBOLS = "onetwothree";
    private static final String ERROR_REASON = "Invalid id";
    private static final String ERROR_MESSAGE = "RecordId values which are exceeded 100 symbols temporarily not allowed";

    private static final int RECORD_ID_MAX_LENGTH = 10;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RecordIdValidator recordIdValidator = new RecordIdValidator(RECORD_ID_MAX_LENGTH);

    @Test
    public void shouldFail_CreateUpdateRecords_ifTooLOngRecordIdPresented() {
        assertEquals(11, RECORD_ID_WITH_101_SYMBOLS.length());

        exceptionRule.expect(AppException.class);
        exceptionRule.expect(buildAppExceptionMatcher(ERROR_MESSAGE, ERROR_REASON));

        recordIdValidator.validateIds(singletonList(RECORD_ID_WITH_101_SYMBOLS));
    }

    @Test
    public void shouldDoNothing_ifNullRecordId_passed() {
        recordIdValidator.validateIds(singletonList(null));
    }


    private Matcher<AppException> buildAppExceptionMatcher(String message, String reason) {
        return new Matcher<AppException>() {
            @Override
            public boolean matches(Object o) {
                AppException appException = (AppException) o;
                AppError error = appException.getError();

                return error.getCode() == 400 &&
                        error.getMessage().equals(message) &&
                        error.getReason().equals(reason);
            }

            @Override
            public void describeMismatch(Object o, Description description) {

            }

            @Override
            public void _dont_implement_Matcher___instead_extend_BaseMatcher_() {

            }

            @Override
            public void describeTo(Description description) {

            }
        };
    }
}