package org.opengroup.osdu.storage.records;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaborationWithoutId;
import static org.opengroup.osdu.storage.util.TestUtils.getCopyRecordRequest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.UUID;
import lombok.extern.java.Log;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;
import org.junit.Test;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.RecordUtil;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

@Log
public abstract class CopyRecordReferencesTest extends TestBase {

  private static final String APPLICATION_NAME = "storage service integration test";

  private static final String COLLABORATION_ID = "25c25830-8588-4b12-a0be-7263f2e43a09";
  private static final String COLLABORATION_ID_WIP_TO_WIP = "cfa0c1b0-421a-4f51-a2ac-84ff8a968736";

  protected static final String RECORD_ID_SOR_TO_WIP =
      TenantUtils.getTenantName() + ":getrecord:" + UUID.randomUUID();

  protected static final String RECORD_ID_WIP_TO_SOR =
      TenantUtils.getTenantName() + ":getrecord:" + UUID.randomUUID();

  protected static final String RECORD_ID_WIP_TO_WIP =
      TenantUtils.getTenantName() + ":getrecord:" + UUID.randomUUID();

  protected static final String RECORD_ID_EXIST_IN_TARGET =
      TenantUtils.getTenantName() + ":getrecord:" + UUID.randomUUID();

  protected static final String KIND = TenantUtils.getTenantName() + ":ds:getrecord:1.0."
      + System.currentTimeMillis();
  protected static String LEGAL_TAG_NAME_A;


  public static void classSetup(String token) throws Exception {
    System.out.println(String.format("Test Ids: %s, %s, %s, %s", RECORD_ID_SOR_TO_WIP, RECORD_ID_WIP_TO_SOR, RECORD_ID_WIP_TO_WIP, RECORD_ID_EXIST_IN_TARGET));
    LEGAL_TAG_NAME_A = LegalTagUtils.createRandomName();
    LegalTagUtils.create(LEGAL_TAG_NAME_A, token);
    Thread.sleep(100);

    String jsonInputSorToWip = RecordUtil.createDefaultJsonRecord(RECORD_ID_SOR_TO_WIP, KIND,
        LEGAL_TAG_NAME_A);

    CloseableHttpResponse responseSorToWip = TestUtils.send("records", "PUT",
        HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), jsonInputSorToWip, "");
    JsonObject jsonResponseSorToWip = JsonParser.parseString(
            EntityUtils.toString(responseSorToWip.getEntity()))
        .getAsJsonObject();

    assertEquals("Creating record for copy from SOR to WIP", 201, responseSorToWip.getCode());
    assertTrue("Creating record for copy from SOR to WIP",
        responseSorToWip.getEntity().getContentType().contains("application/json"));
    assertEquals("Creating record for copy from SOR to WIP", RECORD_ID_SOR_TO_WIP,
        jsonResponseSorToWip.get("recordIds").getAsString());

    String jsonInputWipToSor = RecordUtil.createDefaultJsonRecord(RECORD_ID_WIP_TO_SOR, KIND,
        LEGAL_TAG_NAME_A);

    CloseableHttpResponse responseWipToSor = TestUtils.send("records", "PUT",
        HeaderUtils.getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), token), jsonInputWipToSor, "");
    JsonObject jsonResponseWipToSor = JsonParser.parseString(
            EntityUtils.toString(responseWipToSor.getEntity()))
        .getAsJsonObject();

    assertEquals("Creating record for copy from WIP to SOR", 201, responseWipToSor.getCode());
    assertTrue("Creating record for copy from WIP to SOR",
        responseWipToSor.getEntity().getContentType().contains("application/json"));
    assertEquals("Creating record for copy from WIP to SOR", RECORD_ID_WIP_TO_SOR,
        jsonResponseWipToSor.get("recordIds").getAsString());

    String jsonInputWipToWip = RecordUtil.createDefaultJsonRecord(RECORD_ID_WIP_TO_WIP, KIND,
        LEGAL_TAG_NAME_A);

    CloseableHttpResponse responseWipToWip = TestUtils.send("records", "PUT",
        HeaderUtils.getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), token), jsonInputWipToWip, "");
    JsonObject jsonResponseWipToWip = JsonParser.parseString(
            EntityUtils.toString(responseWipToWip.getEntity()))
        .getAsJsonObject();

    assertEquals("Creating record for copy from WIP to WIP", 201, responseWipToWip.getCode());
    assertTrue("Creating record for copy from WIP to WIP",
        responseWipToWip.getEntity().getContentType().contains("application/json"));
    assertEquals("Creating record for copy from WIP to WIP", RECORD_ID_WIP_TO_WIP,
        jsonResponseWipToWip.get("recordIds").getAsString());

    String jsonInputExistInTarget = RecordUtil.createDefaultJsonRecord(RECORD_ID_EXIST_IN_TARGET,
        KIND,
        LEGAL_TAG_NAME_A);

    CloseableHttpResponse responseExistInTarget = TestUtils.send("records", "PUT",
        HeaderUtils.getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), token), jsonInputExistInTarget, "");
    JsonObject jsonResponseExistInTarget = JsonParser.parseString(
            EntityUtils.toString(responseExistInTarget.getEntity()))
        .getAsJsonObject();

    assertEquals("Creating record for check existing in target", 201,
        responseExistInTarget.getCode());
    assertTrue("Creating record for check existing in target",
        responseExistInTarget.getEntity().getContentType().contains("application/json"));
    assertEquals("Creating record for check existing in target", RECORD_ID_EXIST_IN_TARGET,
        jsonResponseExistInTarget.get("recordIds").getAsString());
  }

  public static void classTearDown(String token) throws Exception {
    TestUtils.send("records/" + RECORD_ID_SOR_TO_WIP, "DELETE",
        HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
    TestUtils.send("records/" + RECORD_ID_WIP_TO_SOR, "DELETE",
        HeaderUtils.getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), token), "", "");
    TestUtils.send("records/" + RECORD_ID_WIP_TO_WIP, "DELETE",
        HeaderUtils.getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), token), "", "");
    Thread.sleep(100);
    LegalTagUtils.delete(LEGAL_TAG_NAME_A, token);
  }

  @Test
  public void should_copyRecord_from_sor_to_wip() throws Exception {
    // check namespace before copy
    CloseableHttpResponse responseGet = TestUtils.send("records/" + RECORD_ID_SOR_TO_WIP, "GET",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    assertEquals("Check that record absent in target when copy from SOR to WIP",
        HttpStatus.SC_NOT_FOUND, responseGet.getCode());
    // copy checks
    JsonObject copyBody = getCopyRecordRequest(COLLABORATION_ID, RECORD_ID_SOR_TO_WIP);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId("", APPLICATION_NAME, TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals("Check response after copy SOR to WIP", HttpStatus.SC_OK, responseCopy.getCode());
    // check namespace after copy
    CloseableHttpResponse responseGetCopy = TestUtils.send("records/" + RECORD_ID_SOR_TO_WIP, "GET",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    JsonObject jsonCopy = JsonParser.parseString(EntityUtils.toString(responseGetCopy.getEntity()))
        .getAsJsonObject();
    assertEquals("Get copied record from WIP when copy SOR to WIP", HttpStatus.SC_OK,
        responseGetCopy.getCode());
    assertEquals("Get copied record from WIP when copy SOR to WIP", RECORD_ID_SOR_TO_WIP,
        jsonCopy.get("id").getAsString());
    // delete from WIP
    CloseableHttpResponse responseDelete = TestUtils.send("records/" + RECORD_ID_SOR_TO_WIP,
        "DELETE", getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), body, "");
    assertEquals("Check that record deleted when copy SOR to WIP", HttpStatus.SC_NO_CONTENT,
        responseDelete.getCode());
  }

  @Test
  public void should_copyRecord_from_wip_to_sor() throws Exception {
    // check namespace before copy
    CloseableHttpResponse responseGet = TestUtils.send("records/" + RECORD_ID_WIP_TO_SOR, "GET",
        HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    assertEquals("Check that record absent in target when copy WIP to SOR", HttpStatus.SC_NOT_FOUND,
        responseGet.getCode());
    // copy checks
    JsonObject copyBody = getCopyRecordRequest("", RECORD_ID_WIP_TO_SOR);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals("Check response after copy WIP to SOR", HttpStatus.SC_OK, responseCopy.getCode());
    // check namespace after copy
    CloseableHttpResponse responseGetCopy = TestUtils.send("records/" + RECORD_ID_WIP_TO_SOR, "GET",
        HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    JsonObject jsonCopy = JsonParser.parseString(EntityUtils.toString(responseGetCopy.getEntity()))
        .getAsJsonObject();
    assertEquals("Get copied record from WIP when copy WIP to SOR", HttpStatus.SC_OK,
        responseGetCopy.getCode());
    assertEquals("Get copied record from WIP when copy WIP to SOR", RECORD_ID_WIP_TO_SOR,
        jsonCopy.get("id").getAsString());
    // delete from SOR
    CloseableHttpResponse responseDelete = TestUtils.send("records/" + RECORD_ID_WIP_TO_SOR,
        "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
        body, "");
    assertEquals("Check that record deleted when copy WIP to SOR", HttpStatus.SC_NO_CONTENT,
        responseDelete.getCode());
  }

  @Test
  public void should_copyRecord_from_wip_to_wip() throws Exception {
    // check namespace before copy
    CloseableHttpResponse responseGet = TestUtils.send("records/" + RECORD_ID_WIP_TO_WIP, "GET",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    assertEquals("Check that record absent in target copy WIP to WIP", HttpStatus.SC_NOT_FOUND,
        responseGet.getCode());
    // copy checks
    JsonObject copyBody = getCopyRecordRequest(COLLABORATION_ID_WIP_TO_WIP, RECORD_ID_WIP_TO_WIP);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals("Check response after copy WIP to WIP", HttpStatus.SC_OK, responseCopy.getCode());
    // check namespace after copy
    CloseableHttpResponse responseGetCopy = TestUtils.send("records/" + RECORD_ID_WIP_TO_WIP, "GET",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), "", "");
    JsonObject jsonCopy = JsonParser.parseString(EntityUtils.toString(responseGetCopy.getEntity()))
        .getAsJsonObject();
    assertEquals("Get copied record from WIP when copy WIP to WIP", HttpStatus.SC_OK,
        responseGetCopy.getCode());
    assertEquals("Get copied record from WIP when copy WIP to WIP", RECORD_ID_WIP_TO_WIP,
        jsonCopy.get("id").getAsString());
    // delete from WIP
    CloseableHttpResponse responseDelete = TestUtils.send("records/" + RECORD_ID_WIP_TO_WIP,
        "DELETE",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID_WIP_TO_WIP, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), body, "");
    assertEquals("Check that record deleted when copy WIP to WIP", HttpStatus.SC_NO_CONTENT,
        responseDelete.getCode());
  }

  @Test
  public void should_return409_when_try_to_copy_sor_to_sor() throws Exception {
    JsonObject copyBody = getCopyRecordRequest("", RECORD_ID_WIP_TO_WIP);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId("", APPLICATION_NAME, TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals(HttpStatus.SC_CONFLICT, responseCopy.getCode());
  }

  @Test
  public void should_return404_when_record_absent_in_source() throws Exception {
    JsonObject copyBody = getCopyRecordRequest(COLLABORATION_ID, RECORD_ID_WIP_TO_SOR);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId("", APPLICATION_NAME, TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals(HttpStatus.SC_NOT_FOUND, responseCopy.getCode());
  }

  @Test
  public void should_return409_when_record_exist_in_target() throws Exception {
    JsonObject copyBody = getCopyRecordRequest("", RECORD_ID_EXIST_IN_TARGET);
    String body = copyBody.toString();

    CloseableHttpResponse responseCopy = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(),
            testUtils.getToken()), body, "");
    assertEquals("Check that record created in WIP when check exception about existing in target",
        HttpStatus.SC_OK, responseCopy.getCode());

    JsonObject copyBodyTest = getCopyRecordRequest("", RECORD_ID_EXIST_IN_TARGET);
    String bodyTest = copyBodyTest.toString();

    CloseableHttpResponse responseCopyTest = TestUtils.send("records/copy", "PUT",
        getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(),
            testUtils.getToken()), bodyTest, "");
    assertEquals("The already exists when check exception about existing in target",
        HttpStatus.SC_CONFLICT, responseCopyTest.getCode());

    // delete from SOR
    CloseableHttpResponse responseDelete = TestUtils.send("records/" + RECORD_ID_EXIST_IN_TARGET,
        "DELETE", getHeadersWithxCollaborationWithoutId(COLLABORATION_ID, APPLICATION_NAME,
            TenantUtils.getTenantName(), testUtils.getToken()), body, "");
    assertEquals("Check that record deleted when check exception about existing in target",
        HttpStatus.SC_NO_CONTENT, responseDelete.getCode());
  }
}
