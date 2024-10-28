/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.records;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class RecordWithEntV2OnlyAclTest extends TestBase {

    private static final long NOW = System.currentTimeMillis();
    private static final String LEGAL_TAG = LegalTagUtils.createRandomName();
    private static final String KIND = TenantUtils.getTenantName() + ":test:inttest:1.1." + NOW;
    private static final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:" + NOW;

    @BeforeEach
    public void setup() throws Exception {
        this.testUtils = new TokenTestUtils();
        LegalTagUtils.create(LEGAL_TAG, testUtils.getToken());
    }

    @AfterEach
    public void tearDown() throws Exception {
        LegalTagUtils.delete(LEGAL_TAG, testUtils.getToken());
        TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        this.testUtils = null;
    }

    @Test
    public void should_allow_recordWithAclThatExistsIOnlyInEntV2() throws Exception{
        //create record
        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                RecordUtil.createJsonRecordWithEntV2OnlyAcl(RECORD_ID, KIND, LEGAL_TAG, RECORD_ID), "");
        assertEquals(HttpStatus.SC_CREATED, response.getCode());
    }

}
