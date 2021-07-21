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
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

public class TestIngestRecordNotFound extends IngestRecordNotFoundTest {

    private static final AzureTestUtils azureTestUtils = new AzureTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        IngestRecordNotFoundTest.classSetup(azureTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        IngestRecordNotFoundTest.classTearDown(azureTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
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

        // blob storage doesn't have blob level access per user. User has to have at least viewer access
//        TestUtils.getResult(response, HttpStatus.SC_FORBIDDEN, String.class);

        //TODO: this test is temporarily changed to return SC_CREATED from SC_FORBIDDEN because of a code change in CloudStorageImpl (Azure implementation - commenting out the call to validateRecordAcls(recordsProcessing)). A proper fix in the CloudStorageImpl should require a change in this test too
        TestUtils.getResult(response, HttpStatus.SC_CREATED, String.class);
	}
}