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

package org.opengroup.osdu.storage.logging;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.http.Request;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.logging.ILogger;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.logging.audit.AuditPayload;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class JaxRsDpsLogTest {

    @Mock
    private ILogger log;

    @Mock
    private DpsHeaders headers;

    @InjectMocks
    private JaxRsDpsLog sut;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(sut, "LOG_PREFIX", "storage");
    }

    @Test
    public void should_writeToAuditLogWithGivenPayload_on_auditRequests() {
        AuditPayload pl = new AuditPayload();
        this.sut.audit(pl);
        verify(this.log).audit(eq("storage.audit"), eq(pl), any());
    }

    @Test
    public void should_writeToRequestLogWithGivenHttpObj_on_requestLog() {
        Request http = Request.builder().build();
        this.sut.request(http);
        verify(this.log).request(eq("storage.request"), eq(http), any());
    }

    @Test
    public void should_writeToAppLogWithGivenMsg_on_errorLogrequest() {
        this.sut.error("error");
        verify(this.log).error(eq("storage.app"), eq("error"), any());
    }

}
