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

import org.apache.http.HttpStatus;
import org.junit.*;

import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class PurgeRecordsIntegrationTest extends TestBase {

	protected static final long NOW = System.currentTimeMillis();

	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":getrecord:" + NOW;
	protected static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0." + NOW;
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
		String jsonInput = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG);

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInput, "");
		assertEquals(201, response.getStatus());
		assertEquals("application/json; charset=UTF-8", response.getType().toString());
	}

	public static void classTearDown(String token) throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_ReturnHttp204_when_purgingRecordSuccessfully() throws Exception {
		ClientResponse response = TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

		response = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
	}
}