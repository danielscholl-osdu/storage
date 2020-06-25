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

package org.opengroup.osdu.storage.jobs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.legal.InvalidTagWithReason;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagConsistencyValidator;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.service.LegalServiceImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class LegalTagConsistencyValidatorTest {

    private LegalTagChangedCollection toBeValidatedDto = new LegalTagChangedCollection();

    private Set<String> tagNames = new HashSet<>();

    @InjectMocks
    private LegalTagConsistencyValidator sut;

    @Mock
    private LegalServiceImpl legalService;

    @Mock
    private JaxRsDpsLog logger;

    @Before
    public void setup() {
        initMocks(this);

        LegalTagChanged tagFromMessage1 = new LegalTagChanged();
        tagFromMessage1.setChangedTagStatus("incompliant");
        tagFromMessage1.setChangedTagName("test-legaltag-1");
        LegalTagChanged tagFromMessage2 = new LegalTagChanged();
        tagFromMessage2.setChangedTagName("test-legaltag-2");
        tagFromMessage2.setChangedTagStatus("incompliant");

        List<LegalTagChanged> tagsFromMessage = new ArrayList<>();
        tagsFromMessage.add(tagFromMessage1);
        tagsFromMessage.add(tagFromMessage2);

        this.toBeValidatedDto.setStatusChangedTags(tagsFromMessage);

        this.tagNames.add("test-legaltag-1");
        this.tagNames.add("test-legaltag-2");
    }

    @Test
    public void should_returnOnlyConsistentTagsFromMessage_whenTagsFromMessageNotConsistentWithLegal() {
        InvalidTagWithReason invalidTag = new InvalidTagWithReason();
        invalidTag.setName("test-legaltag-1");
        invalidTag.setReason("LegalTag not found");

        InvalidTagWithReason[] response = new InvalidTagWithReason[] { invalidTag };

        LegalTagChanged tagFromLegal = new LegalTagChanged();
        tagFromLegal.setChangedTagStatus("incompliant");
        tagFromLegal.setChangedTagName("test-legaltag-1");
        List<LegalTagChanged> tagsFromLegal = new ArrayList<>();
        tagsFromLegal.add(tagFromLegal);
        LegalTagChangedCollection expected = new LegalTagChangedCollection();
        expected.setStatusChangedTags(tagsFromLegal);

        when(this.legalService.getInvalidLegalTags(this.tagNames)).thenReturn(response);
        try {
            LegalTagChangedCollection validatedTags = this.sut
                    .checkLegalTagStatusWithLegalService(this.toBeValidatedDto);
            Assert.assertEquals(expected, validatedTags);
            verify(this.logger).warning("Inconsistency between pubsub message and legal: test-legaltag-2. Expected incompliant.");
        } catch (Exception e) {
            fail("should not throw exception");
        }
    }
}
