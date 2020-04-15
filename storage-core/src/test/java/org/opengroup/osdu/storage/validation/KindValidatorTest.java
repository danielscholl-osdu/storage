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

import org.junit.Test;
import org.opengroup.osdu.core.common.model.storage.validation.KindValidator;

public class KindValidatorTest {

    @Test
    public void should_validateKinds() {

        KindValidator validator = new KindValidator();
        assertTrue(validator.isValid("valid:kind:test:1.0.0", null));
        assertFalse(validator.isValid("withoutcolon", null));
        assertFalse(validator.isValid("without:colon", null));
        assertFalse(validator.isValid("without:version:value", null));
        assertFalse(validator.isValid("123:321:value", null));
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        KindValidator validator = new KindValidator();
        validator.initialize(null);
    }

    @Test
    public void should_validateWhetherKindMatchesTenantName() {
        assertTrue(KindValidator.isKindFromTenantValid("TeNanT1:kind:test:1.0.9", "tenANT1"));
        assertFalse(KindValidator.isKindFromTenantValid("Tenant1:kind:test:1.0.9", "tenanT1t"));
        assertFalse(KindValidator.isKindFromTenantValid("Tenant1kind:test:1.0.9", "tenanT1"));
    }
}
