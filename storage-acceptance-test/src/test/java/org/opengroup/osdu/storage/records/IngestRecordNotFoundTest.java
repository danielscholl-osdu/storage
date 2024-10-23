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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public final class IngestRecordNotFoundTest extends TestBase {

	private static final long NOW = System.currentTimeMillis();
	private static final String LEGAL_TAG = LegalTagUtils.createRandomName();

	private static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1." + NOW;
	private static final String RECORD_ID = TenantUtils.getTenantName() + ":endtoend:1.1." + NOW;

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG, token);
	}

	public static void classTearDown(String token) throws Exception {
		LegalTagUtils.delete(LEGAL_TAG, token);
	}

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		IngestRecordNotFoundTest.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		IngestRecordNotFoundTest.classTearDown(TOKEN_TEST_UTILS.getToken());
	}

	@BeforeEach
	@Override
	public void setup() throws Exception {
		this.testUtils = new TokenTestUtils();
	}

	@AfterEach
	@Override
	public void tearDown() throws Exception {
		this.testUtils = null;
	}

	@Test
	public void should_returnBadRequest_when_userGroupDoesNotExist() throws Exception {

		String group = String.format("data.thisDataGrpDoesNotExsist@%s", TestUtils.getAclSuffix());

		String record = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG).replace(TestUtils.getAcl(), group);

		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), record, "");

		String result = TestUtils.getResult(response, HttpStatus.SC_BAD_REQUEST, String.class);
		JsonObject jsonResponse = JsonParser.parseString(result).getAsJsonObject();
		assertEquals("Error on writing record", jsonResponse.get("reason").getAsString());
		assertEquals("Could not find group \"" + group + "\".",
				jsonResponse.get("message").getAsString());
	}
}
