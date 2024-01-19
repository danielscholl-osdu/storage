package org.opengroup.osdu.storage.provider.azure.util;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.util.ReflectionUtils.findField;

@ExtendWith(MockitoExtension.class)
class RecordUtilTest {
    private static final String RECORD_ID_WITH_11_SYMBOLS = "onetwothree";
    private static final String RECORD_ID_ENDING_WITH_DOT = "id.";
    private static final String RECORD_ID_ENDING_WITH_BACKSLASH = "id\\";
    private static final String RECORD_ID_ENDING_WITH_FORWARDSLASH = "id/";
    private static final String ERROR_REASON = "Invalid id";
    private static final String ERROR_MESSAGE = "RecordId values which are exceeded 100 symbols temporarily not allowed";
    private static final String UNSUPPORTED_CHARACTER_ERROR_MESSAGE = "RecordId values ending in dot (.), backslash (\\), or forward slash (/) not allowed";
    private static final Long VERSION = 10000L;
    private static final String WRONG_VERSION = "11111";
    private static final String VERSION_SEQUENCE = "1";
    private static final String KIND = "kind";

    private static final int RECORD_ID_MAX_LENGTH = 10;

    private final RecordUtil recordUtil = new RecordUtil();

    @BeforeEach
    void setup() {
        Field recordIdMaxLength = findField(RecordUtil.class, "recordIdMaxLength");
        recordIdMaxLength.setAccessible(true);
        ReflectionUtils.setField(recordIdMaxLength, recordUtil, RECORD_ID_MAX_LENGTH);
    }

    @Test
    void shouldFail_CreateUpdateRecords_ifTooLOngRecordIdPresented() {
        assertEquals(11, RECORD_ID_WITH_11_SYMBOLS.length());
        List<String> listToBeValidated = Arrays.asList(RECORD_ID_WITH_11_SYMBOLS, RECORD_ID_WITH_11_SYMBOLS);

        AppException appException = assertThrows(AppException.class, () -> recordUtil.validateIds(listToBeValidated));

        assertEquals(HttpStatus.SC_BAD_REQUEST, appException.getError().getCode());
        assertEquals(ERROR_MESSAGE, appException.getError().getMessage());
        assertEquals(ERROR_REASON, appException.getError().getReason());

    }

    @Test
    public void shouldFail_CreateUpdateRecords_ifRecordEndsWithUnsupportedCharacter() {
        List<String> listToBeValidated = Arrays.asList(RECORD_ID_ENDING_WITH_BACKSLASH, RECORD_ID_ENDING_WITH_DOT, RECORD_ID_ENDING_WITH_FORWARDSLASH);

        AppException appException = assertThrows(AppException.class, () -> recordUtil.validateIds(listToBeValidated));

        assertEquals(HttpStatus.SC_BAD_REQUEST, appException.getError().getCode());
        assertEquals(UNSUPPORTED_CHARACTER_ERROR_MESSAGE, appException.getError().getMessage());
        assertEquals(ERROR_REASON, appException.getError().getReason());
    }

    @Test
    void shouldDoNothing_ifNullRecordId_passed() {
        recordUtil.validateIds(singletonList(null));
    }

    @Test
    void shouldGetKindForVersion_successFully() {
        RecordMetadata record = buildRecordMetadata();

        String actualKind = recordUtil.getKindForVersion(record, VERSION.toString());

        assertEquals(KIND, actualKind);
    }

    @Test
    void shouldFailGetKindForVersion_whenVersionNotFound() {
        String errorMessage = String.format("The version %s can't be found for record %s",
                WRONG_VERSION, RECORD_ID_WITH_11_SYMBOLS);
        String errorReason = "Version not found";

        RecordMetadata recordMetadata = buildRecordMetadata();

        AppException appException = assertThrows(AppException.class, () -> recordUtil.getKindForVersion(recordMetadata, WRONG_VERSION));

        assertEquals(HttpStatus.SC_NOT_FOUND, appException.getError().getCode());
        assertEquals(errorMessage, appException.getError().getMessage());
        assertEquals(errorReason, appException.getError().getReason());
    }

    @Test
    void shouldFailGetKindForVersion_whenVersionMatches_onlySequence() {
        String errorMessage = String.format("The version %s can't be found for record %s",
                VERSION_SEQUENCE, RECORD_ID_WITH_11_SYMBOLS);
        String errorReason = "Version not found";

        RecordMetadata recordMetadata = buildRecordMetadata();

        AppException appException = assertThrows(AppException.class, () -> recordUtil.getKindForVersion(recordMetadata, VERSION_SEQUENCE));

        assertEquals(HttpStatus.SC_NOT_FOUND, appException.getError().getCode());
        assertEquals(errorMessage, appException.getError().getMessage());
        assertEquals(errorReason, appException.getError().getReason());
    }

    private RecordMetadata buildRecordMetadata() {
        RecordMetadata recordMetadata = new RecordMetadata();
        recordMetadata.setId(RECORD_ID_WITH_11_SYMBOLS);
        recordMetadata.setKind(KIND);
        recordMetadata.addGcsPath(VERSION);
        recordMetadata.getGcsVersionPaths().add(null);
        return recordMetadata;
    }
}
