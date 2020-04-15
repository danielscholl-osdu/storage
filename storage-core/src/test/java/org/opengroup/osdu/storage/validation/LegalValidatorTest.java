// Copyright 2017-2019, Schlumberger
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.google.common.collect.Sets;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.Record;
import org.opengroup.osdu.core.common.model.storage.RecordAncestry;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.legal.validation.LegalValidator;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;

@RunWith(MockitoJUnitRunner.class)
public class LegalValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    private ConstraintViolationBuilder constraintBuilder = mock(ConstraintViolationBuilder.class);

    private Record record;

    private LegalValidator sut;

    @Before
    public void setup() {
        this.sut = new LegalValidator();
        this.record = new Record();
    }

    @Test
    public void should_returnFalse_when_parentRecordIdDoesNotFollowRecordVersionNamingConvetion() {

        final String EXPECTED_MSG = "Invalid parent record format: 'record:without:version'. The following format is expected: {record-id}:{record-version}";

        when(this.context.buildConstraintViolationWithTemplate(EXPECTED_MSG)).thenReturn(this.constraintBuilder);

        RecordAncestry ancestry = new RecordAncestry();
        ancestry.setParents(Sets.newHashSet("record:without:version"));

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record.setLegal(legal);
        this.record.setAncestry(ancestry);

        assertFalse(this.sut.isValid(this.record, this.context));

        verify(this.context).buildConstraintViolationWithTemplate(EXPECTED_MSG);
    }

    @Test
    public void should_returnFalse_when_parentRecordVersionIsNotANumber() {

        final String EXPECTED_MSG = "Invalid parent record version: 'record:without:version:notNumber'. Record version must be a numeric value";

        when(this.context.buildConstraintViolationWithTemplate(EXPECTED_MSG)).thenReturn(this.constraintBuilder);

        RecordAncestry ancestry = new RecordAncestry();
        ancestry.setParents(Sets.newHashSet("record:without:version:notNumber"));

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record.setLegal(legal);
        this.record.setAncestry(ancestry);

        assertFalse(this.sut.isValid(this.record, this.context));

        verify(this.context).buildConstraintViolationWithTemplate(EXPECTED_MSG);
    }

    @Test
    public void should_returnTrue_when_parentRecordIsProvided() {

        RecordAncestry ancestry = new RecordAncestry();
        ancestry.setParents(Sets.newHashSet("record:without:version:123"));

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record.setLegal(legal);
        this.record.setAncestry(ancestry);

        assertTrue(this.sut.isValid(this.record, this.context));
    }

    @Test
    public void should_returnFalse_when_neitherParentNorLegalTagsAreProvided() {

        when(this.context.buildConstraintViolationWithTemplate(ValidationDoc.RECORD_LEGAL_TAGS_NOT_EMPTY))
                .thenReturn(this.constraintBuilder);

        Legal legal = new Legal();
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record.setAncestry(new RecordAncestry());
        this.record.setLegal(legal);

        assertFalse(this.sut.isValid(this.record, this.context));

        this.record.setLegal(legal);

        assertFalse(this.sut.isValid(this.record, this.context));

        verify(this.context, times(2)).buildConstraintViolationWithTemplate(ValidationDoc.RECORD_LEGAL_TAGS_NOT_EMPTY);
    }

    @Test
    public void should_returnTrue_when_noParentIsProvidedButValidLegalTagsAndOrdc() {

        Legal legal = new Legal();
        legal.setLegaltags(Sets.newHashSet("legal1"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.record.setLegal(legal);

        assertTrue(this.sut.isValid(this.record, this.context));
    }

    @Test
    public void should_doNothing_inValidatorInitialize() {
        // for coverage purposes only
        this.sut.initialize(null);
    }
}