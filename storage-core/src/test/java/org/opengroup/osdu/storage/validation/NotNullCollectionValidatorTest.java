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

import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import org.opengroup.osdu.core.common.model.storage.validation.NotNullCollectionValidator;

public class NotNullCollectionValidatorTest {

    private NotNullCollectionValidator sut;

    @Before
    public void setup() {
        this.sut = new NotNullCollectionValidator();
    }

    @Test
    public void should_returnTrue_when_validatingEmptyOrNullCollection() {
        assertTrue(this.sut.isValid(null, null));
        assertTrue(this.sut.isValid(new ArrayList<>(), null));
    }

    @Test
    public void should_returnFalse_when_validatingCollectionWithNullValue() {
        assertFalse(this.sut.isValid(Lists.newArrayList("test", null, "fail"), null));
    }

    @Test
    public void should_returnTrue_when_validatingCollectionWithNonNullValue() {
        assertTrue(this.sut.isValid(Lists.newArrayList("test", "another one", "fail"), null));
    }

    @Test
    public void should_doNothing_inValidatorInitialize() {
        // for coverage purposes only
        this.sut.initialize(null);
    }
}