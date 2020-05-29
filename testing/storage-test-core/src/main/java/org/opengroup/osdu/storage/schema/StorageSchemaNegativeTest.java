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

import javax.ws.rs.HttpMethod;

import org.apache.http.HttpStatus;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;
import com.sun.jersey.api.client.ClientResponse;

public abstract class StorageSchemaNegativeTest extends TestBase {

	protected final String SCHEMAS = "schemas";
	protected final String kind = TenantUtils.getTenantName() + ":storage:inttest:1.0.0"
			+ System.currentTimeMillis();
	protected final String path = String.format("%s/%s", this.SCHEMAS, this.kind);

	@Test
	public void should_notCreateSchema_when_userDoesWrongKindFormat() throws Exception {
		String kind = "abc";
		JsonElement jsonInputRecord = createSchemaPayload(kind);
		ClientResponse recordResponse = TestUtils.send(this.SCHEMAS, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				jsonInputRecord.toString(), "");
		assertEquals(HttpStatus.SC_BAD_REQUEST, recordResponse.getStatus());
	}

	@Test
	public void should_notCreateSchema_when_schemaAlreadyExists() throws Exception {
		JsonElement jsonInputRecord = createSchemaPayload(this.kind);
		ClientResponse recordResponse = TestUtils.send(this.SCHEMAS, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				jsonInputRecord.toString(), "");
		assertEquals(HttpStatus.SC_CREATED, recordResponse.getStatus());
		ClientResponse recordResponseCreateAgain = TestUtils.send(this.SCHEMAS, HttpMethod.POST,
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), jsonInputRecord.toString(), "");
		assertEquals(HttpStatus.SC_CONFLICT, recordResponseCreateAgain.getStatus());
		System.out.println("2. Waiting..."+System.currentTimeMillis());
		Thread.sleep(80000);
		ClientResponse deleteResponse = TestUtils.send(this.path, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		System.out.println(deleteResponse.getStatus());
		System.out.println(deleteResponse.getStatusInfo());
		assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getStatus());
	}

	@Test
	public void should_notGetKindDetails_when_userDoesSpecifyNonExistingKind() throws Exception {
		String kind = "abc";
		ClientResponse recordResponse = TestUtils.send(this.SCHEMAS + "/" + kind, HttpMethod.GET,
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_BAD_REQUEST, recordResponse.getStatus());
	}

	@Test
	public void should_notGetSchemaDetails_when_thereIsNoSchemaExist() throws Exception {
		JsonElement jsonInputRecord = createSchemaPayload(this.kind);
		ClientResponse recordResponse = TestUtils.send(this.SCHEMAS, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				jsonInputRecord.toString(), "");
		assertEquals(HttpStatus.SC_CREATED, recordResponse.getStatus());
		ClientResponse deleteResponse = TestUtils.send(this.path, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getStatus());
		ClientResponse recordResponseNotFound = TestUtils.send(this.path, HttpMethod.GET, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"");
		assertEquals(HttpStatus.SC_NOT_FOUND, recordResponseNotFound.getStatus());

	}

	@Test
	public void should_notDeleteSchemaByUsingKind_when_userDoesNotSpecifyWrongKind() throws Exception {
		ClientResponse deleteResponseWithNoKind = TestUtils.send(this.SCHEMAS + "/" + "abc", HttpMethod.DELETE,
				HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
		assertEquals(HttpStatus.SC_BAD_REQUEST, deleteResponseWithNoKind.getStatus());
	}

	@Test
	public void should_notDeleteSchemaByUsingKind_when_no_schemaExist() throws Exception {
		JsonElement jsonInputRecord = createSchemaPayload(this.kind);
		ClientResponse recordResponse = TestUtils.send(this.SCHEMAS, HttpMethod.POST, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
				jsonInputRecord.toString(), "");
		assertEquals(HttpStatus.SC_CREATED, recordResponse.getStatus());
		ClientResponse deleteResponse = TestUtils.send(this.path, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");

		assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.getStatus());
		ClientResponse deleteResponseAgain = TestUtils.send(this.path, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "",
				"");

		assertEquals(HttpStatus.SC_NOT_FOUND, deleteResponseAgain.getStatus());
	}

	public static JsonObject createSchemaPayload(String kind) {

		JsonObject record = new JsonObject();
		record.addProperty("kind", kind);

		JsonObject innerSchemaObjectOne = new JsonObject();
		innerSchemaObjectOne.addProperty("path", "name");
		innerSchemaObjectOne.addProperty("kind", "string");
		JsonObject innerSchemaObjectTwo = new JsonObject();
		innerSchemaObjectTwo.addProperty("path", "age");
		innerSchemaObjectTwo.addProperty("kind", "int");

		JsonArray schema = new JsonArray();
		schema.add(innerSchemaObjectOne);
		schema.add(innerSchemaObjectTwo);

		record.add("schema", schema);

		return record;
	}
}
