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

package org.opengroup.osdu.storage.records;

import com.sun.jersey.api.client.ClientResponse;

import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

public class TestIngestRecordNotFound extends IngestRecordNotFoundTest {

    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        IngestRecordNotFoundTest.classSetup(awsTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        IngestRecordNotFoundTest.classTearDown(awsTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }
    
    @Override
	@Test
	public void should_returnBadRequest_when_userGroupDoesNotExist() throws Exception {

		String group = String.format("data.thisDataGrpDoesNotExsist@%s", TestUtils.getAclSuffix());

		String record = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG).replace(TestUtils.getAcl(), group);
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), record, "");
        // it's a much simpler implementation to just check if the user is in the group that is being saved and if not to skip
        // per previous integration test requirements
        TestUtils.getResult(response, HttpStatus.SC_FORBIDDEN, String.class);
	}
}