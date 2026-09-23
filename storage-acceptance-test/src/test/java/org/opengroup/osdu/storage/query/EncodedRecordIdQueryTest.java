package org.opengroup.osdu.storage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opengroup.osdu.core.test.client.ClientException;
import org.opengroup.osdu.core.test.client.HttpResponse;
import org.opengroup.osdu.core.test.client.model.storage.CreateRecordsResponse;
import org.opengroup.osdu.core.test.client.model.storage.QueryRecordsRequest;
import org.opengroup.osdu.core.test.client.model.storage.Records;
import org.opengroup.osdu.core.test.client.model.storage.StorageRecord;
import org.opengroup.osdu.storage.records.BaseRecordsAcceptanceTest;
import org.opengroup.osdu.storage.util.RecordUtil;

@Slf4j
public final class EncodedRecordIdQueryTest extends BaseRecordsAcceptanceTest {

  private String legalTag;
  private String kind;
  private String recordIdWithSpecialChars;
  private String recordIdWithSpecialChars2;
  private String createdRecordId;

  @BeforeEach
  @Override
  protected void setup() throws Exception {
    super.setup();
    assumeTrue(configUtils.getBooleanProperty("enableEncodedSpecialCharactersInURL", "false"),
        "enableEncodedSpecialCharactersInURL must be true");
    createdRecordId = null;
    legalTag = createLegalTagName("");
    createLegalTag(legalTag);
    kind = getTenantId() + ":test:endtoend:1.1." + System.currentTimeMillis();
    recordIdWithSpecialChars =
        getTenantId() + ":endtoend:specialchars%20:1.1." + System.currentTimeMillis();
    recordIdWithSpecialChars2 =
        getTenantId() + ":endtoend:specialcharsagain%20:1.1." + System.currentTimeMillis();
  }

  @AfterEach
  @Override
  protected void teardown() {
    // Tidy teardown uses an unencoded path; %xx in the id is decoded by HTTP and DELETE fails with 400.
    deleteRecordWithEncodedPath(createdRecordId);
    legalTagClient.teardown();
    entitlementsClient.teardown();
  }

  @Test
  public void should_preserveEncodedIdWithSpecialChars_when_recordIsQueriedViaGetAPI() {
    StorageRecord[] records =
        withTestAcl(RecordUtil.createDefaultRecords(recordIdWithSpecialChars, kind, legalTag));

    HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(records);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    assertEquals(recordIdWithSpecialChars, createResponse.body().recordIds()[0]);
    createdRecordId = recordIdWithSpecialChars;

    HttpResponse<StorageRecord> getResponse =
        storageClient.getRecord(encodeRecordIdForPath(recordIdWithSpecialChars));
    assertEquals(HttpStatus.SC_OK, getResponse.statusCode());
    assertEquals(recordIdWithSpecialChars, getResponse.body().id());
  }

  @Test
  public void should_preserveEncodedIdWithSpecialChars_when_recordIsQueriedViaPostAPI() {
    StorageRecord[] records =
        withTestAcl(RecordUtil.createDefaultRecords(recordIdWithSpecialChars2, kind, legalTag));

    HttpResponse<CreateRecordsResponse> createResponse = storageClient.putRecords(records);
    assertEquals(HttpStatus.SC_CREATED, createResponse.statusCode());
    assertEquals(recordIdWithSpecialChars2, createResponse.body().recordIds()[0]);
    createdRecordId = recordIdWithSpecialChars2;

    var queryResponse =
        storageClient.queryRecordsPost(QueryRecordsRequest.of(recordIdWithSpecialChars2));
    assertEquals(HttpStatus.SC_OK, queryResponse.statusCode());

    Records responseObject = queryResponse.body();
    assertEquals(1, responseObject.records().length);
    assertEquals(0, responseObject.invalidRecords().length);
    assertEquals(0, responseObject.retryRecords().length);
    assertEquals(recordIdWithSpecialChars2, responseObject.records()[0].id());
  }

  private static String encodeRecordIdForPath(String recordId) {
    return URLEncoder.encode(recordId, StandardCharsets.UTF_8);
  }

  private void deleteRecordWithEncodedPath(String recordId) {
    if (recordId == null) {
      return;
    }
    try {
      storageClient.deleteRecord(encodeRecordIdForPath(recordId));
    } catch (ClientException ex) {
      log.warn("Failed to delete record '{}' during teardown: {}", recordId, ex.getMessage(), ex);
    }
  }
}
