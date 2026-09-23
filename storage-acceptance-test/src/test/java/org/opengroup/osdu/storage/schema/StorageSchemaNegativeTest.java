package org.opengroup.osdu.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.storage.BaseStorageAcceptanceTest;
import org.opengroup.osdu.storage.util.SchemaPayloadUtil;

public final class StorageSchemaNegativeTest extends BaseStorageAcceptanceTest {

  private static final String SCHEMAS = "schemas";

  private String kind;
  private String path;

  @BeforeEach
  @Override
  public void setup() throws Exception {
    super.setup();
    kind = getTenantId() + ":storage:inttest:1.0.0" + System.currentTimeMillis();
    path = SCHEMAS + "/" + kind;
  }

  @Test
  public void should_notCreateSchema_when_userDoesWrongKindFormat() throws Exception {
    String invalidKind = "abc";
    JsonElement jsonInputRecord = SchemaPayloadUtil.createSchemaPayload(invalidKind);
    HttpResponse<String> recordResponse =
        send(SCHEMAS, Method.POST, "", jsonInputRecord.toString());
    assertEquals(HttpStatus.SC_BAD_REQUEST, recordResponse.statusCode());
  }

  @Test
  public void should_notCreateSchema_when_schemaAlreadyExists() throws Exception {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      JsonElement jsonInputRecord = SchemaPayloadUtil.createSchemaPayload(kind);
      HttpResponse<String> recordResponse =
          send(SCHEMAS, Method.POST, "", jsonInputRecord.toString());
      assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
      HttpResponse<String> recordResponseCreateAgain =
          send(SCHEMAS, Method.POST, "", jsonInputRecord.toString());
      assertEquals(HttpStatus.SC_CONFLICT, recordResponseCreateAgain.statusCode());
      HttpResponse<String> deleteResponse = send(path, Method.DELETE);
      assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
    }
  }

  @Test
  public void should_notGetKindDetails_when_userDoesSpecifyNonExistingKind() throws Exception {
    String invalidKind = "abc";
    HttpResponse<String> recordResponse =
        send(SCHEMAS + "/" + invalidKind, Method.GET);
    assertEquals(HttpStatus.SC_BAD_REQUEST, recordResponse.statusCode());
  }

  @Test
  public void should_notGetSchemaDetails_when_thereIsNoSchemaExist() throws Exception {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      JsonElement jsonInputRecord = SchemaPayloadUtil.createSchemaPayload(kind);
      HttpResponse<String> recordResponse =
          send(SCHEMAS, Method.POST, "", jsonInputRecord.toString());
      assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
      HttpResponse<String> deleteResponse = send(path, Method.DELETE);
      assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
      HttpResponse<String> recordResponseNotFound = send(path, Method.GET);
      assertEquals(HttpStatus.SC_NOT_FOUND, recordResponseNotFound.statusCode());
    }
  }

  @Test
  public void should_notDeleteSchemaByUsingKind_when_userDoesNotSpecifyWrongKind() throws Exception {
    HttpResponse<String> deleteResponseWithNoKind =
        send(SCHEMAS + "/abc", Method.DELETE);
    assertEquals(HttpStatus.SC_BAD_REQUEST, deleteResponseWithNoKind.statusCode());
  }

  @Test
  public void should_notDeleteSchemaByUsingKind_when_no_schemaExist() throws Exception {
    if (configUtils.getIsSchemaEndpointsEnabled()) {
      JsonElement jsonInputRecord = SchemaPayloadUtil.createSchemaPayload(kind);
      HttpResponse<String> recordResponse =
          send(SCHEMAS, Method.POST, "", jsonInputRecord.toString());
      assertEquals(HttpStatus.SC_CREATED, recordResponse.statusCode());
      HttpResponse<String> deleteResponse = send(path, Method.DELETE);
      assertEquals(HttpStatus.SC_NO_CONTENT, deleteResponse.statusCode());
      HttpResponse<String> deleteResponseAgain = send(path, Method.DELETE);
      assertEquals(HttpStatus.SC_NOT_FOUND, deleteResponseAgain.statusCode());
    }
  }
}
