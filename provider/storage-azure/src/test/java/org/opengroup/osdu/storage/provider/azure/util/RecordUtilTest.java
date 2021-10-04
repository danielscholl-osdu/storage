package org.opengroup.osdu.storage.provider.azure.util;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.springframework.util.ReflectionUtils.findField;

@RunWith(MockitoJUnitRunner.class)
public class RecordUtilTest {
    private static final String RECORD_ID_WITH_11_SYMBOLS = "onetwothree";
    private static final String ERROR_REASON = "Invalid id";
    private static final String ERROR_MESSAGE = "RecordId values which are exceeded 100 symbols temporarily not allowed";
    private static final Long VERSION = 10000L;
    private static final String WRONG_VERSION = "11111";
    private static final String KIND = "kind";

    private static final int RECORD_ID_MAX_LENGTH = 10;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private RecordUtil recordUtil = new RecordUtil();

    @Before
    public void setup() {
        Field recordIdMaxLength = findField(RecordUtil.class, "recordIdMaxLength");
        recordIdMaxLength.setAccessible(true);
        ReflectionUtils.setField(recordIdMaxLength, recordUtil, RECORD_ID_MAX_LENGTH);
    }

    @Test
    public void shouldFail_CreateUpdateRecords_ifTooLOngRecordIdPresented() {
        assertEquals(11, RECORD_ID_WITH_11_SYMBOLS.length());

        exceptionRule.expect(AppException.class);
        exceptionRule.expect(buildAppExceptionMatcher(ERROR_MESSAGE, ERROR_REASON, 400));

        recordUtil.validateIds(Arrays.asList(RECORD_ID_WITH_11_SYMBOLS, RECORD_ID_WITH_11_SYMBOLS));
    }

    @Test
    public void shouldDoNothing_ifNullRecordId_passed() {
        recordUtil.validateIds(singletonList(null));
    }

    @Test
    public void shouldGetKindForVersion_successFully() {
        RecordMetadata record = buildRecordMetadata();

        String actualKind = recordUtil.getKindForVersion(record, VERSION.toString());

        assertEquals(KIND, actualKind);
    }

    @Test
    public void shouldFailGetKindForVersion_whenVersionNotFound() {
        String errorMessage = String.format("The version %s can't be found for record %s",
                WRONG_VERSION, RECORD_ID_WITH_11_SYMBOLS);
        String errorReason = "Version not found";

        RecordMetadata record = buildRecordMetadata();

        exceptionRule.expect(AppException.class);
        exceptionRule.expect(buildAppExceptionMatcher(errorMessage, errorReason, 500));

        recordUtil.getKindForVersion(record, WRONG_VERSION);
    }

    private RecordMetadata buildRecordMetadata() {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(RECORD_ID_WITH_11_SYMBOLS);
        recordMetadata.setKind(KIND);
        recordMetadata.addGcsPath(VERSION);
        recordMetadata.getGcsVersionPaths().add(null);
        return  recordMetadata;
    }

    private Matcher<AppException> buildAppExceptionMatcher(String message, String reason, int errorCode) {
        return new Matcher<AppException>() {
            @Override
            public boolean matches(Object o) {
                AppException appException = (AppException) o;
                AppError error = appException.getError();

                return error.getCode() == errorCode &&
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