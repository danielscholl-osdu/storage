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

package org.opengroup.osdu.storage.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.records.RecordsApiAcceptanceTests;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;

public final class StressTests extends TestBase {

	private static final String RECORD_ID = TenantUtils.getTenantName()
			+ ":WG-Multi-Client:flatten-full-seismic-2d-shape_survey_2d_0623_Survey2D_Angola_Lower_Congo_2D_Repro_AWG98_26_1";

	private static String LEGAL_TAG_NAME = LegalTagUtils.createRandomName();

	private static final String KIND = TenantUtils.getTenantName() + ":ds:inttest:1.0."
			+ System.currentTimeMillis();

	private static final TokenTestUtils TOKEN_TEST_UTILS = new TokenTestUtils();

	@BeforeAll
	public static void classSetup() throws Exception {
		StressTests.classSetup(TOKEN_TEST_UTILS.getToken());
	}

	@AfterAll
	public static void classTearDown() throws Exception {
		StressTests.classTearDown(TOKEN_TEST_UTILS.getToken());
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

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG_NAME, token);
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/", "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", RECORD_ID);
		LegalTagUtils.delete(LEGAL_TAG_NAME, token);
	}

	@Test
	public void should_create100Records_when_givenValidRecord() throws Exception {
		this.performanceTestCreateAndUpdateRecord(100);
	}

	@Test
	public void should_create10Records_when_givenValidRecord() throws Exception {
		this.performanceTestCreateAndUpdateRecord(10);
	}

	@Test
	public void should_create1Records_when_givenValidRecord() throws Exception {
		this.performanceTestCreateAndUpdateRecord(1);
	}

	protected void performanceTestCreateAndUpdateRecord(int capacity) throws Exception {
		String json = "";
		List<String> ids = new ArrayList<>(capacity);
		for (int i = 0; i < capacity; i++) {
			String id1 = TenantUtils.getTenantName() + ":inttest:" + System.currentTimeMillis() + i;
			json += RecordsApiAcceptanceTests.singleEntityBody(id1, "ash ketchum", KIND, LEGAL_TAG_NAME);
			if (i != capacity - 1) {
				json += ",";
			}
			ids.add(id1);
		}

		json = "[" + json + "]";

		long startMillis = System.currentTimeMillis();
		CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "");
		long totalMillis = System.currentTimeMillis() - startMillis;
		System.out.println(String.format("Took %s milliseconds to Create %s 1KB records", totalMillis, ids.size()));

		String responseJson = EntityUtils.toString(response.getEntity());
		System.out.println(responseJson);
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		assertTrue(response.getEntity().getContentType().toString().contains("application/json"));
		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(responseJson,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertEquals(capacity, result.recordCount);
		assertEquals(capacity, result.recordIds.length);
		assertEquals(capacity, result.recordIdVersions.length);

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "?skipdupes=false");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		System.out.println(String.format("Took %s milliseconds to Update %s 1KB records", totalMillis, ids.size()));

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "?skipdupes=false");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(HttpStatus.SC_CREATED, response.getCode());
		System.out.println(String.format("Took %s milliseconds to Update %s 1KB records when when skipdupes is true",
				totalMillis, ids.size()));

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records/" + ids.get(0), "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(HttpStatus.SC_OK, response.getCode());
		System.out.println(String.format("Took %s milliseconds to GET 1 1KB record", totalMillis));

		ids.parallelStream().forEach((id) -> {
			try {
				TestUtils.send("records/" + id, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
			} catch (Exception e) {
			}
		});
	}
}
