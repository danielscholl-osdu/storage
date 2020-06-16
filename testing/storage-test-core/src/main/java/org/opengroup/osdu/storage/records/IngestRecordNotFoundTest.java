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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class IngestRecordNotFoundTest extends TestBase {

	protected static final long NOW = System.currentTimeMillis();
	protected static final String LEGAL_TAG = LegalTagUtils.createRandomName();

	protected static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1." + NOW;
	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":testendtoend:1.1." + NOW;

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
	}

	public static void classTearDown(String token) throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	@Test
	public void should_returnBadRequest_when_userGroupDoesNotExist() throws Exception {

		String group = String.format("data.thisDataGrpDoesNotExsist@%s", TestUtils.getAclSuffix());

		String record = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG).replace(TestUtils.getAcl(), group);

		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), record, "");

		String result = TestUtils.getResult(response, HttpStatus.SC_BAD_REQUEST, String.class);
		JsonObject jsonResponse = new JsonParser().parse(result).getAsJsonObject();
		assertEquals("Error on writing record", jsonResponse.get("reason").getAsString());
		assertEquals("Could not find group \"" + group + "\".",
				jsonResponse.get("message").getAsString());
	}
}