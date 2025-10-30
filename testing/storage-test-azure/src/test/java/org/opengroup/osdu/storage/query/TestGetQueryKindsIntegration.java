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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

public class TestGetQueryKindsIntegration extends GetQueryKindsIntegrationTests {

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AzureTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
        this.configUtils = null;
	}

	@Test
	@Override
	public void should_returnBadRequest_when_dataPartitionIDIsMissing() throws Exception {
		CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeadersWithoutDataPartitionId(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=2");
		assertTrue(EntityUtils.toString(response.getEntity()).contains("data-partition-id header is missing"));
		assertEquals(HttpStatus.SC_BAD_REQUEST, response.getCode());
	}

	@Test
	@Override
	public void should_returnNotFoundOrUnauthorized_when_dataPartitionIDIsInvalid() throws Exception {
		String invalidTestDataPartitionId = "test-data-partition";
		CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeaders(invalidTestDataPartitionId, testUtils.getToken()), "", "?limit=2");
		assertTrue(EntityUtils.toString(response.getEntity()).contains(String.format("%s partition not found", invalidTestDataPartitionId)));
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getCode());
	}
}