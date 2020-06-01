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

package org.opengroup.osdu.storage.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.http.HttpStatus;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import com.sun.jersey.api.client.ClientResponse;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

public abstract class CreateSchemaIntegrationTests extends TestBase {

	protected final String schema = TenantUtils.getTenantName() + ":storage:inttest:1.0.0"
			+ System.currentTimeMillis();

	@Test
	public void should_createSchema_and_returnHttp409IfTryToCreateItAgain_and_getSchema_and_deleteSchema_when_providingValidSchemaInfo()
			throws Exception {
		String body = this.validPostBody(this.schema);

		// Create schema
		ClientResponse response = TestUtils.send("schemas", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		assertEquals(HttpStatus.SC_CREATED, response.getStatus());
		assertEquals("", response.getEntity(String.class));

		// Try to create again
		response = TestUtils.send("schemas", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), body, "");
		assertEquals(HttpStatus.SC_CONFLICT, response.getStatus());
		assertEquals(
				"{\"code\":409,\"reason\":\"Schema already registered\",\"message\":\"The schema information for the given kind already exists.\"}",
				response.getEntity(String.class));

		// Get the schema
		response = TestUtils.send("schemas/" + this.schema, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_OK, response.getStatus());

		JsonParser parser = new JsonParser();
		JsonObject json = parser.parse(response.getEntity(String.class)).getAsJsonObject();

		assertEquals(this.schema, json.get("kind").getAsString());
		assertEquals(2, json.get("schema").getAsJsonArray().size());
		assertEquals("name", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("path").getAsString());
		assertEquals("string", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("kind").getAsString());
		assertEquals("call911", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("ext")
				.getAsJsonObject().get("indexerTip").getAsString());

		assertEquals("age", json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("path").getAsString());
		assertEquals("int", json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("kind").getAsString());

		assertEquals(2, json.get("ext").getAsJsonObject().size());
		assertEquals("this is a weird string", json.get("ext").getAsJsonObject().get("address.city").getAsString());
		assertEquals("country with two letters",
				json.get("ext").getAsJsonObject().get("address.country").getAsString());

		// get schema by a user belonging to another tenant, make sure DpsHeader/Tenant is per request rather than singleton
		response = TestUtils.send("schemas/" + this.schema, "GET", HeaderUtils.getHeaders("common", testUtils.getToken()), "", "");
		assertTrue(HttpStatus.SC_FORBIDDEN == response.getStatus() || HttpStatus.SC_UNAUTHORIZED == response.getStatus());

		// Delete schema
		response = TestUtils.send("schemas/" + this.schema, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

		response = TestUtils.send("schemas/" + this.schema, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());
	}

	protected String validPostBody(String kind) {

		JsonObject item1Ext = new JsonObject();
		item1Ext.addProperty("indexerTip", "call911");

		JsonObject item1 = new JsonObject();
		item1.addProperty("path", "name");
		item1.addProperty("kind", "string");
		item1.add("ext", item1Ext);

		JsonObject item2Ext = new JsonObject();
		item2Ext.addProperty("address.city", "this is a weird string");
		item2Ext.addProperty("address.country", "country with two letters");

		JsonObject item2 = new JsonObject();
		item2.addProperty("path", "age");
		item2.addProperty("kind", "int");
		item2.add("ext", item2Ext);

		JsonArray schemaItems = new JsonArray();
		schemaItems.add(item1);
		schemaItems.add(item2);

		JsonObject schema = new JsonObject();
		schema.addProperty("kind", kind);
		schema.add("schema", schemaItems);
		schema.add("ext", item2Ext);

		return schema.toString();
	}
}