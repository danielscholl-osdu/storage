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

package org.opengroup.osdu.storage.PubsubEndpoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.opengroup.osdu.storage.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public abstract class PubsubEndpointTest extends TestBase {
	protected static final long NOW = System.currentTimeMillis();
	protected static final long FIVE_SECOND_LATER = NOW + 5000L;
	protected static final String LEGAL_TAG_1 = LegalTagUtils.createRandomName();
	protected static final String LEGAL_TAG_2 = LEGAL_TAG_1 + "random2";

	protected static final String KIND = TenantUtils.getTenantName() + ":test:endtoend:1.1." + NOW;
	protected static final String RECORD_ID = TenantUtils.getTenantName() + ":endtoend:1.1." + NOW;
	protected static final String RECORD_ID_2 = TenantUtils.getTenantName() + ":endtoend:1.1."
			+ FIVE_SECOND_LATER;
	protected static final String PUBSUB_TOKEN = TestUtils.getPubsubToken();

	public static void classSetup(String token) throws Exception {
		LegalTagUtils.create(LEGAL_TAG_1, token);
		String record1 = RecordUtil.createDefaultJsonRecord(RECORD_ID, KIND, LEGAL_TAG_1);
		CloseableHttpResponse responseValid = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), record1, "");
		Assert.assertEquals(HttpStatus.SC_CREATED, responseValid.getCode());

		LegalTagUtils.create(LEGAL_TAG_2, token);
		String record2 = RecordUtil.createDefaultJsonRecord(RECORD_ID_2, KIND, LEGAL_TAG_2);
		CloseableHttpResponse responseValid2 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), record2, "");
		Assert.assertEquals(HttpStatus.SC_CREATED, responseValid2.getCode());
	}

	public static void classTearDown(String token) throws Exception {
		TestUtils.send("records/" + RECORD_ID, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
		TestUtils.send("records/" + RECORD_ID_2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

		LegalTagUtils.delete(LEGAL_TAG_1, token);
		LegalTagUtils.delete(LEGAL_TAG_2, token);
	}

	@Test
	public void should_deleteIncompliantLegaltagAndInvalidateRecordsAndNotIngestAgain_whenIncompliantMessageSentToEndpoint()
			throws Exception {
		LegalTagUtils.delete(LEGAL_TAG_1, testUtils.getToken());
		// wait until cache of opa will be rebuild
		Thread.sleep(150000);

		List<String> legalTagNames = new ArrayList<>();
		legalTagNames.add(LEGAL_TAG_1);
		legalTagNames.add(LEGAL_TAG_2);

		String requestBody = this.requestBodyToEndpoint(legalTagNames);
		CloseableHttpResponse responseEndpoint = TestUtils.send("push-handlers/legaltag-changed?token=" + PUBSUB_TOKEN, "POST",
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
		System.out.println(" getEntity"  + EntityUtils.toString(responseEndpoint.getEntity()));
		Assert.assertEquals(HttpStatus.SC_OK, responseEndpoint.getCode());
		System.out.println("ok");
		CloseableHttpResponse responseRecordQuery = TestUtils.send("records/" + RECORD_ID, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"");
		Assert.assertEquals(HttpStatus.SC_NOT_FOUND, responseRecordQuery.getCode());

		long now = System.currentTimeMillis();
		long later = now + 2000L;
		String recordIdTemp1 = TenantUtils.getTenantName() + ":endtoend:1.1." + now;
		String kindTemp = TenantUtils.getTenantName() + ":test:endtoend:1.1." + now;
		String recordTemp1 = RecordUtil.createDefaultJsonRecord(recordIdTemp1, kindTemp, LEGAL_TAG_1);
		String recordIdTemp2 = TenantUtils.getTenantName() + ":endtoend:1.1." + later;
		String recordTemp2 = RecordUtil.createDefaultJsonRecord(recordIdTemp2, kindTemp, LEGAL_TAG_2);

		CloseableHttpResponse responseInvalid = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), recordTemp1, "");
		Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, responseInvalid.getCode());
		Assert.assertEquals("Invalid legal tags", this.getResponseReasonFromRecordIngestResponse(responseInvalid));
		CloseableHttpResponse responseValid3 = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), recordTemp2, "");
		Assert.assertEquals(HttpStatus.SC_CREATED, responseValid3.getCode());
		TestUtils.send("records/" + recordIdTemp2, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
	}

	protected String requestBodyToEndpoint(List<String> legalTagNames) throws Exception {
		JsonObject attributes = new JsonObject();
		attributes.addProperty("data-partition-id", TenantUtils.getTenantName());
		attributes.addProperty("user", "integrationtest@gmail.com");

		JsonArray statusChangedTags = new JsonArray();

		for (String legalTagName : legalTagNames) {
			JsonObject statusChangedTag = new JsonObject();
			statusChangedTag.addProperty("changedTagName", legalTagName);
			statusChangedTag.addProperty("changedTagStatus", "incompliant");
			statusChangedTags.add(statusChangedTag);
		}

		JsonObject data = new JsonObject();
		data.add("statusChangedTags", statusChangedTags);

		String encoded = new String(Base64.getEncoder().encode(data.toString().getBytes()));

		JsonObject message = new JsonObject();
		message.addProperty("messageId", "integration-test-message-id");
		message.addProperty("data", encoded);
		message.add("attributes", attributes);

		JsonObject output = new JsonObject();
		output.add("message", message);

		return output.toString();
	}

	protected String getResponseReasonFromRecordIngestResponse(CloseableHttpResponse response) {
		JsonObject json = null;
		try {
			json = new JsonParser().parse(EntityUtils.toString(response.getEntity())).getAsJsonObject();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		return json.get("reason").getAsString();
	}

}
