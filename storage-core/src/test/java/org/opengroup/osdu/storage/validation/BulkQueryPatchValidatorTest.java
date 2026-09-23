// Copyright 2017-2023, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.validation.impl.BulkQueryPatchValidator;

import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.DUPLICATE_RECORD_ID;
import static org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc.INVALID_PAYLOAD;
import static org.opengroup.osdu.storage.validation.ValidationDoc.INVALID_RECORD_ID_PATCH;
import static org.opengroup.osdu.storage.validation.ValidationDoc.PATCH_RECORDS_MAX;
import static org.opengroup.osdu.storage.validation.ValidationDoc.RECORD_ID_LIST_NOT_EMPTY;

@ExtendWith(MockitoExtension.class)
public class BulkQueryPatchValidatorTest {
    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private IFeatureFlag featureFlag;

    private BulkQueryPatchValidator sut;

    @BeforeEach
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
    public void should_throwValidationException_ifRecordListIsNull() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        recordQueryPatch.setIds(null);
        exceptionRulesAndMethodRun(recordQueryPatch, RECORD_ID_LIST_NOT_EMPTY);
    }

    @Test
    public void should_throwValidationException_ifRecordListIsEmpty() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        List<String> recordIds = new ArrayList<>();
        recordQueryPatch.setIds(recordIds);
        exceptionRulesAndMethodRun(recordQueryPatch, RECORD_ID_LIST_NOT_EMPTY);
    }

    @Test
    public void should_throwValidationException_ifRecordListHasMoreThan100Ids() {
        RecordQueryPatch recordQueryPatch = new RecordQueryPatch();
        List<String> recordIds = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            recordIds.add("id" + i);
        }
        recordQueryPatch.setIds(recordIds);
        exceptionRulesAndMethodRun(recordQueryPatch, PATCH_RECORDS_MAX);
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
        RequestValidationException exception = assertThrows(RequestValidationException.class, ()->{
                sut.isValid(recordQueryPatch, context);
        });
        assertEquals(errorMessage, exception.getMessage());
    }
}
