package org.opengroup.osdu.storage.HeaderValidations;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.storage.util.*;

import jakarta.ws.rs.HttpMethod;

public abstract class ValidateRequiredHeaders extends TestBase {
    protected static final String RECORD = "records";
    protected static final String KIND_ONE = TenantUtils.getTenantName() + ":test:endtoend:1.1."
            + System.currentTimeMillis();
    protected static final String KIND_ID_ONE = TenantUtils.getTenantName() + ":endtoend:1.1."
            + System.currentTimeMillis();
    protected static final String KIND_VERSION_ID = TenantUtils.getTenantName() + ":endtoend:1.2."
            + System.currentTimeMillis();
    protected static final String LEGAL_TAG_NAME = LegalTagUtils.createRandomName();

    public static void classSetup(String token) throws Exception {
        LegalTagUtils.create(LEGAL_TAG_NAME, token);
    }

    public static void classTearDown(String token) throws Exception {
        TestUtils.send(RECORD + "/" + KIND_ID_ONE, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        TestUtils.send(RECORD + "/" + KIND_VERSION_ID, HttpMethod.DELETE, HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");

        LegalTagUtils.delete(LEGAL_TAG_NAME, token);
    }

    protected CloseableHttpResponse createTestRecordWithoutAuth(String kind, String id, String legalName) throws Exception {
        String jsonInputRecord = RecordUtil.createDefaultJsonRecord(id, kind, legalName);
        return TestUtils.send(RECORD, HttpMethod.PUT, HeaderUtils.getHeadersWithoutAuth(TenantUtils.getTenantName(), testUtils.getToken()), jsonInputRecord, "");
    }

    protected CloseableHttpResponse createTestRecordWithoutDataPartitionID(String kind, String id, String legalName) throws Exception {
        String jsonInputRecord = RecordUtil.createDefaultJsonRecord(id, kind, legalName);
        return TestUtils.send(RECORD, HttpMethod.PUT, HeaderUtils.getHeadersWithoutDataPartitionId(TenantUtils.getTenantName(), testUtils.getToken()), jsonInputRecord, "");
    }

    @Test
    public void ValidateMissingAuthHeaderReturnsUnauthorizedError() throws Exception {
        CloseableHttpResponse recordResponse = createTestRecordWithoutAuth(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
        //validate that the error code is either 401/403 since for some its 403 I guess at some
        //other level like istio etc.
        Assert.assertTrue(recordResponse.getCode() == 401 || recordResponse.getCode() == 403);
    }

    @Test
    public void ValidateMissingDataPartitionHeaderReturnsBadRequestError() throws Exception {
        CloseableHttpResponse recordResponse = createTestRecordWithoutDataPartitionID(KIND_ONE, KIND_ID_ONE, LEGAL_TAG_NAME);
        AppError recordResult = TestUtils.getResult(recordResponse, HttpStatus.SC_BAD_REQUEST,
                AppError.class);

        AppError expectedError = new AppError(HttpStatus.SC_BAD_REQUEST, "Bad Request", "data-partition-id header is missing");

        Assert.assertEquals(recordResult, expectedError);
    }
}
