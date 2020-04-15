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

import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.entitlements.validation.AclValidator;
import org.opengroup.osdu.core.common.model.storage.validation.ValidationDoc;

@RunWith(MockitoJUnitRunner.class)
public class AclValidatorTest {

    private static final String INVALID_GROUP = "nodata.email@gmail.com";
    private static final String[] VALUES = new String[] { "data.email1@dom.dev.cloud.dom-ds.com",
            "data.test@dom.dev.cloud.dom-ds.com" };

    private Acl acl;

    private AclValidator sut;

    @Mock
    private ConstraintValidatorContext context;

    @Before
    public void setup() {
        this.sut = new AclValidator();
        this.acl = new Acl();

        ConstraintViolationBuilder builder = mock(ConstraintViolationBuilder.class);
        when(this.context.buildConstraintViolationWithTemplate(ValidationDoc.RECORD_ACL_VIEWERS_NOT_EMPTY))
                .thenReturn(builder);
        when(this.context.buildConstraintViolationWithTemplate(ValidationDoc.RECORD_ACL_OWNERS_NOT_EMPTY))
                .thenReturn(builder);
        when(this.context.buildConstraintViolationWithTemplate("Invalid group name 'nodata.email@gmail.com'"))
                .thenReturn(builder);
    }

    @Test
    public void should_doNothingInInitialize() {
        // for coverage purposes. Do nothing method!
        this.sut.initialize(null);
    }

    @Test
    public void should_returnFalse_when_AclViewersIsNullOrEmpty() {

        this.acl.setOwners(VALUES);
        assertFalse(this.sut.isValid(this.acl, this.context));

        this.acl.setViewers(new String[] {});
        assertFalse(this.sut.isValid(this.acl, this.context));

        verify(this.context, times(2)).buildConstraintViolationWithTemplate(ValidationDoc.RECORD_ACL_VIEWERS_NOT_EMPTY);
    }

    @Test
    public void should_returnFalse_when_AclOwnersIsNullOrEmpty() {

        this.acl.setViewers(VALUES);
        assertFalse(this.sut.isValid(this.acl, this.context));

        this.acl.setOwners(new String[] {});
        assertFalse(this.sut.isValid(this.acl, this.context));

        verify(this.context, times(2)).buildConstraintViolationWithTemplate(ValidationDoc.RECORD_ACL_OWNERS_NOT_EMPTY);
    }

    @Test
    public void should_returnFalse_when_AclViewerHasInvalidGroupName() {

        this.acl.setViewers(new String[] { INVALID_GROUP });
        this.acl.setOwners(VALUES);
        assertFalse(this.sut.isValid(this.acl, this.context));

        verify(this.context).buildConstraintViolationWithTemplate("Invalid group name 'nodata.email@gmail.com'");
    }

    @Test
    public void should_returnFalse_when_AclOwnerHasInvalidGroupName() {

        this.acl.setViewers(VALUES);
        this.acl.setOwners(new String[] { INVALID_GROUP });

        assertFalse(this.sut.isValid(this.acl, this.context));

        verify(this.context).buildConstraintViolationWithTemplate("Invalid group name 'nodata.email@gmail.com'");
    }

    @Test
    public void should_returnTrue_when_AclHasValidValues() {

        this.acl.setViewers(VALUES);
        this.acl.setOwners(VALUES);

        assertTrue(this.sut.isValid(this.acl, this.context));
    }

    @Test
    public void should_applyRegex_when_validatingAcl() {
        assertTrue("data.valid@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertTrue("data.valid.valid.valid@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertTrue("data.valid@gmail.com".matches(ValidationDoc.EMAIL_REGEX));

        assertFalse("data.valid@gmailcom".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("data@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("data.@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("dat@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("invalid@tenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("data.validtenant.env.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
        assertFalse("Amdata.valid@tenantenv.cloud.dom-ds.com".matches(ValidationDoc.EMAIL_REGEX));
    }
}
