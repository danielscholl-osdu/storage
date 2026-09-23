package org.opengroup.osdu.storage.schema;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.service.ServiceType;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.SchemaPayloadUtil;

import static org.junit.jupiter.api.Assertions.*;

public final class CreateSchemaIntegrationTest extends BaseStorageAcceptanceTest {

  private static final String SCHEMAS = "schemas";

  private String schema;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    schema = getTenantId() + ":storage:inttest:1.0.0" + System.currentTimeMillis();
  }

  @Test
  public void should_createSchema_and_returnHttp409IfTryToCreateItAgain_and_getSchema_and_deleteSchema_when_providingValidSchemaInfo()
      throws Exception {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      String body = SchemaPayloadUtil.validPostBody(schema);

      HttpResponse<String> response = send(SCHEMAS, Method.POST, "", body);
      assertEquals(HttpStatus.SC_CREATED, response.statusCode());
      assertNull(response.body());

      response = send(SCHEMAS, Method.POST, "", body);
      assertEquals(HttpStatus.SC_CONFLICT, response.statusCode());
      assertEquals(
          "{\"code\":409,\"reason\":\"Schema already registered\",\"message\":\"The schema information for the given kind already exists.\"}",
          response.body());

      response = send(SCHEMAS + "/" + schema, Method.GET);
      assertEquals(HttpStatus.SC_OK, response.statusCode());

      JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

      assertEquals(schema, json.get("kind").getAsString());
      assertEquals(2, json.get("schema").getAsJsonArray().size());
      assertEquals("name",
          json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("path").getAsString());
      assertEquals("string",
          json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("kind").getAsString());
      assertEquals("call911", json.get("schema").getAsJsonArray().get(0).getAsJsonObject().get("ext")
          .getAsJsonObject().get("indexerTip").getAsString());

      assertEquals("age",
          json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("path").getAsString());
      assertEquals("int",
          json.get("schema").getAsJsonArray().get(1).getAsJsonObject().get("kind").getAsString());

      assertEquals(2, json.get("ext").getAsJsonObject().size());
      assertEquals("this is a weird string",
          json.get("ext").getAsJsonObject().get("address.city").getAsString());
      assertEquals("country with two letters",
          json.get("ext").getAsJsonObject().get("address.country").getAsString());

      response = send(UserType.PRIVILEGED_USER, ServiceType.STORAGE_V2, SCHEMAS + "/" + schema,
          Method.GET, "", null, Map.of("data-partition-id", "common"));
      assertTrue(response.statusCode() == HttpStatus.SC_FORBIDDEN
          || response.statusCode() == HttpStatus.SC_UNAUTHORIZED);

      response = send(SCHEMAS + "/" + schema, Method.DELETE);
      assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode());

      response = send(SCHEMAS + "/" + schema, Method.GET);
      assertEquals(HttpStatus.SC_NOT_FOUND, response.statusCode());
    }
  }
}
