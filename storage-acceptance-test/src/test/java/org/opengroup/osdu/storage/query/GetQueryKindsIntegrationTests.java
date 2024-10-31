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

package org.opengroup.osdu.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import org.opengroup.osdu.storage.util.TokenTestUtils;


public final class GetQueryKindsIntegrationTests extends TestBase {

	private static final DummyRecordsHelper RECORD_HELPER = new DummyRecordsHelper();

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
	public void should_returnMax1000Results_when_settingLimitToAValueLessThan1() throws Exception {
		if (configUtils != null && configUtils.getIsSchemaEndpointsEnabled()) {
			CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=0");
			assertEquals(HttpStatus.SC_OK, response.getCode());

			DummyRecordsHelper.QueryResultMock responseObject = RECORD_HELPER.getQueryResultMockFromResponse(response);

			assertTrue(responseObject.results.length > 1 && responseObject.results.length <= 1000);
		}
	}

	@Test
	public void should_return400ErrorResult_when_givingAnInvalidCursorParameter() throws Exception {
		if (configUtils != null && configUtils.getIsSchemaEndpointsEnabled()) {
			CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
					"?cursor=badCursorString");
			assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());

      assertEquals(
          "{\"code\":400,\"reason\":\"Cursor invalid\",\"message\":\"The requested cursor does not exist or is invalid\"}",
          EntityUtils.toString(response.getEntity()));
		}
	}

	@Test
	public void should_return2Results_when_requesting2Items() throws Exception {
			if (configUtils != null && configUtils.getIsSchemaEndpointsEnabled()) {
			CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=2");
			assertEquals(HttpStatus.SC_OK, response.getCode());

			DummyRecordsHelper.QueryResultMock responseObject = RECORD_HELPER.getQueryResultMockFromResponse(response);

			assertEquals(2, responseObject.results.length);
		}
	}
}
