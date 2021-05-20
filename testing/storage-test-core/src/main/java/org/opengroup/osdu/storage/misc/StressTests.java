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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.*;

import com.google.gson.Gson;
import org.opengroup.osdu.storage.records.RecordsApiAcceptanceTests;
import org.opengroup.osdu.storage.util.*;
import com.sun.jersey.api.client.ClientResponse;

public abstract class StressTests extends TestBase {

	protected static final String RECORD_ID = TenantUtils.getTenantName()
			+ ":WG-Multi-Client:flatten-full-seismic-2d-shape_survey_2d_0623_Survey2D_Angola_Lower_Congo_2D_Repro_AWG98_26_1";

	protected static String LEGAL_TAG_NAME = LegalTagUtils.createRandomName();

	protected static final String KIND = TenantUtils.getTenantName() + ":ds:inttest:1.0."
			+ System.currentTimeMillis();

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
		ClientResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "");
		long totalMillis = System.currentTimeMillis() - startMillis;
		System.out.println(String.format("Took %s milliseconds to Create %s 1KB records", totalMillis, ids.size()));

		String responseJson = response.getEntity(String.class);
		System.out.println(responseJson);
		assertEquals(201, response.getStatus());
		assertTrue(response.getType().toString().contains("application/json"));
		Gson gson = new Gson();
		DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(responseJson,
				DummyRecordsHelper.CreateRecordResponse.class);
		assertEquals(capacity, result.recordCount);
		assertEquals(capacity, result.recordIds.length);
		assertEquals(capacity, result.recordIdVersions.length);

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "?skipdupes=false");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(201, response.getStatus());
		System.out.println(String.format("Took %s milliseconds to Update %s 1KB records", totalMillis, ids.size()));

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "?skipdupes=false");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(201, response.getStatus());
		System.out.println(String.format("Took %s milliseconds to Update %s 1KB records when when skipdupes is true",
				totalMillis, ids.size()));

		startMillis = System.currentTimeMillis();
		response = TestUtils.send("records/" + ids.get(0), "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		totalMillis = System.currentTimeMillis() - startMillis;
		assertEquals(200, response.getStatus());
		System.out.println(String.format("Took %s milliseconds to GET 1 1KB record", totalMillis));

		ids.parallelStream().forEach((id) -> {
			try {
				TestUtils.send("records/" + id, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
			} catch (Exception e) {
			}
		});
	}
}