// Copyright © 2020 Amazon Web Services
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.*;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;
import com.google.gson.Gson;



public class TestRecordsApiAcceptance extends RecordsApiAcceptanceTests {

    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
	public static void classSetup() throws Exception {
        RecordsApiAcceptanceTests.classSetup(awsTestUtils.getToken());
	}

	@AfterClass
	public static void classTearDown() throws Exception {
        RecordsApiAcceptanceTests.classTearDown(awsTestUtils.getToken());
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
    public void should_createNewRecord_withSpecialCharacter_ifEnabled() throws Exception {
		final long currentTimeMillis = System.currentTimeMillis();
		final String RECORD_ID = TenantUtils.getTenantName() + ":inttest:testSpecialChars%abc%2Ffoobar-" + currentTimeMillis;
		final String ENCODED_RECORD_ID = TenantUtils.getTenantName() + ":inttest:testSpecialChars%25abc%252Ffoobar-" + currentTimeMillis;

		String jsonInput = createJsonBody(RECORD_ID, "TestSpecialCharacters");

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInput, "");
		String json = response.getEntity(String.class);
		assertEquals(201, response.getStatus());
		assertTrue(response.getType().toString().contains("application/json"));

		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(json,
				DummyRecordsHelper.CreateRecordResponse.class);

		assertEquals(1, result.recordCount);
		assertEquals(1, result.recordIds.length);
		assertEquals(1, result.recordIdVersions.length);
		assertEquals(RECORD_ID, result.recordIds[0]);

		response = TestUtils.send("records/" + ENCODED_RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");


		
		// Service does not allow URLs with suspicious characters, Which is the default setting.
		// Different CSPs are responding with different status code for this error when a special character like %25 is present in the URL.
		// Hence the Assert Statement is marked not to be 200.
		// More details - https://community.opengroup.org/osdu/platform/system/storage/-/issues/61
		assertNotEquals(200, response.getStatus());
	}

}
