package org.opengroup.osdu.storage.opa;

import com.google.common.collect.Sets;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.opa.model.OpaError;
import org.opengroup.osdu.storage.opa.model.ValidationOutputRecord;
import org.opengroup.osdu.storage.opa.service.OPAServiceConfig;
import org.opengroup.osdu.storage.opa.service.OPAServiceImpl;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OPAServiceImplTest {
    @Mock
    private DpsHeaders headers;

    @Mock
    private JaxRsDpsLog logger;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse httpResponse;

    @Mock
    private OPAServiceConfig opaServiceConfig;

    @InjectMocks
    private OPAServiceImpl sut;

    private static final String RECORD_ID1 = "tenant1:kind:record1";
    private static final String RECORD_ID2 = "tenant1:crazy:record2";
    private static final String KIND_1 = "tenant1:test:kind:1.0.0";
    private static final String KIND_2 = "tenant1:test:crazy:2.0.2";
    private static final String NEW_USER = "newuser@gmail.com";
    private static final String TENANT = "tenant1";
    private static final String URL = "null/v1/data/osdu/partition/tenant1/dataauthz/records";
    private static final String[] ACL = new String[] { "data.email1@tenant1.gmail.com", "data.test@tenant1.gmail.com" };
    private static Map<String, String> opaQueryParams;

    private Acl acl;

    @BeforeAll
    public static void setupTestClass() {
        opaQueryParams = new HashMap<>();
        opaQueryParams.put(DpsHeaders.CORRELATION_ID, "cor-id");
        opaQueryParams.put(DpsHeaders.DATA_PARTITION_ID, TENANT);
        opaQueryParams.put(DpsHeaders.USER_ID, "user-id");
    }

    @BeforeEach
    public void setup() {
        sut.setHttpClient(httpClient);
        this.acl = new Acl();

        Legal legal = new Legal();
        legal.setLegaltags(Sets.newHashSet("legaltag1", "legaltag2"));
        legal.setOtherRelevantDataCountries(Sets.newHashSet("FRA"));

        this.acl.setViewers(ACL);
        this.acl.setOwners(ACL);

        lenient().when(this.headers.getPartitionIdWithFallbackToAccountId()).thenReturn(TENANT);
        lenient().when(this.headers.getAuthorization()).thenReturn("Bearer testtoken");
        when(this.headers.getPartitionId()).thenReturn(TENANT);
        when(this.headers.getCorrelationId()).thenReturn("cor-id");
        when(this.headers.getUserId()).thenReturn("user-id");
    }

    @Test
    public void shouldThrowAppException_whenOpaDataAuthorizationCheckCallFails() {
        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(this.acl);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);
        existingRecordMetadata2.setKind(KIND_2);
        existingRecordMetadata2.setStatus(RecordState.active);
        existingRecordMetadata2.setAcl(this.acl);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(existingRecordMetadata1);
        recordsMetadata.add(existingRecordMetadata2);

        HttpRequest httpRequest = HttpRequest.builder()
                .url(URL)
                .httpMethod(HttpMethod.POST.name())
                .queryParams(opaQueryParams)
                .body(getExpectedRequestBody("update")).build();
        when(httpClient.send(httpRequest)).thenReturn(httpResponse);

        when(httpResponse.isSuccessCode()).thenReturn(false);
        when(httpResponse.getResponseCode()).thenReturn(400);
        when(httpResponse.getBody()).thenReturn("response body");

        try {
            List<ValidationOutputRecord> response = this.sut.validateUserAccessToRecords(recordsMetadata, OperationType.update);
            fail("should not proceed.");
        } catch (AppException ex) {
            verify(logger, times(1)).warning("Failure when calling OPA with response code 400, response body: response body");
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getError().getCode());
            assertEquals("error getting data authorization result", ex.getError().getMessage());
        }
    }

    @Test
    public void shouldThrowAppException_whenOpaDataAuthorizationPolicyIsUndefined() {
        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(this.acl);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);
        existingRecordMetadata2.setKind(KIND_2);
        existingRecordMetadata2.setStatus(RecordState.active);
        existingRecordMetadata2.setAcl(this.acl);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(existingRecordMetadata1);
        recordsMetadata.add(existingRecordMetadata2);

        HttpRequest httpRequest = HttpRequest.builder()
                .url(URL)
                .httpMethod(HttpMethod.POST.name())
                .queryParams(opaQueryParams)
                .body(getExpectedRequestBody("update")).build();
        when(httpClient.send(httpRequest)).thenReturn(httpResponse);

        when(httpResponse.isSuccessCode()).thenReturn(true);
        lenient().when(httpResponse.getResponseCode()).thenReturn(200);
        when(httpResponse.getBody()).thenReturn("{}");

        try {
            List<ValidationOutputRecord> response = this.sut.validateUserAccessToRecords(recordsMetadata, OperationType.update);
            fail("should not proceed.");
        } catch (AppException ex) {
            verify(logger, times(1)).warning("Data Authorization Policy is undefined.");
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getError().getCode());
            assertEquals("error getting data authorization result", ex.getError().getMessage());
        }
    }

    @Test
    public void shouldReturnListOfValidationOutputRecords_whenOpaDataAuthorizationCheckForCreateOrUpdateRecordsCompletesSuccessfully() {
        RecordMetadata existingRecordMetadata1 = new RecordMetadata();
        existingRecordMetadata1.setUser(NEW_USER);
        existingRecordMetadata1.setKind(KIND_1);
        existingRecordMetadata1.setStatus(RecordState.active);
        existingRecordMetadata1.setAcl(this.acl);

        RecordMetadata existingRecordMetadata2 = new RecordMetadata();
        existingRecordMetadata2.setUser(NEW_USER);
        existingRecordMetadata2.setKind(KIND_2);
        existingRecordMetadata2.setStatus(RecordState.active);
        existingRecordMetadata2.setAcl(this.acl);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(existingRecordMetadata1);
        recordsMetadata.add(existingRecordMetadata2);

        HttpRequest httpRequest = HttpRequest.builder()
                .url(URL)
                .httpMethod(HttpMethod.POST.name())
                .queryParams(opaQueryParams)
                .body(getExpectedRequestBody("update")).build();
        when(httpClient.send(httpRequest)).thenReturn(httpResponse);

        when(httpResponse.isSuccessCode()).thenReturn(true);
        lenient().when(httpResponse.getResponseCode()).thenReturn(200);
        when(httpResponse.getBody()).thenReturn("{\"result\": [{\"errors\": [],\"id\": \"tenant1:kind:record1\"},{\"errors\": [{\"message\":\"Invalid legal tag(s) found on record\"},{\"message\":\"You must be an owner to update a record\"}],\"id\": \"tenant1:crazy:record2\"}]}");

        List<OpaError> errors2 = new ArrayList<>();
        errors2.add(OpaError.builder().message("Invalid legal tag(s) found on record").build());
        errors2.add(OpaError.builder().message("You must be an owner to update a record").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(Collections.EMPTY_LIST).build();
        ValidationOutputRecord validationOutputRecord2 = ValidationOutputRecord.builder().id(RECORD_ID2).errors(errors2).build();
        List<ValidationOutputRecord> expectedValidationOutputRecords = new ArrayList<>();
        expectedValidationOutputRecords.add(validationOutputRecord1);
        expectedValidationOutputRecords.add(validationOutputRecord2);

        List<ValidationOutputRecord> response = this.sut.validateUserAccessToRecords(recordsMetadata, OperationType.update);
        assertEquals(expectedValidationOutputRecords, response);
    }

    @Test
    public void shouldReturnListOfValidationOutputRecords_whenOpaDataAuthorizationCheckCompletesSuccessfully() {
        RecordMetadata recordMetadata1 = new RecordMetadata();
        recordMetadata1.setUser(NEW_USER);
        recordMetadata1.setKind(KIND_1);
        recordMetadata1.setStatus(RecordState.active);
        recordMetadata1.setAcl(this.acl);

        RecordMetadata recordMetadata2 = new RecordMetadata();
        recordMetadata2.setUser(NEW_USER);
        recordMetadata2.setKind(KIND_2);
        recordMetadata2.setStatus(RecordState.active);
        recordMetadata2.setAcl(this.acl);

        List<RecordMetadata> recordsMetadata = new ArrayList<>();
        recordsMetadata.add(recordMetadata1);
        recordsMetadata.add(recordMetadata2);

        HttpRequest httpRequest = HttpRequest.builder()
                .url(URL)
                .httpMethod(HttpMethod.POST.name())
                .queryParams(opaQueryParams)
                .body(getExpectedRequestBody("view")).build();
        when(httpClient.send(httpRequest)).thenReturn(httpResponse);

        when(httpResponse.isSuccessCode()).thenReturn(true);
        lenient().when(httpResponse.getResponseCode()).thenReturn(200);
        when(httpResponse.getBody()).thenReturn("{\"result\": [{\"errors\": [],\"id\": \"tenant1:kind:record1\"},{\"id\": \"tenant1:crazy:record2\",\"errors\":[{\"reason\":\"test\",\"message\":\"You must be a viewer or an owner to access a record\",\"code\":\"401\",\"id\": \"tenant1:crazy:record2\"}]}]}");

        List<OpaError> errors2 = new ArrayList<>();
        errors2.add(OpaError.builder().message("You must be a viewer or an owner to access a record").code("401").reason("test").id("tenant1:crazy:record2").build());
        ValidationOutputRecord validationOutputRecord1 = ValidationOutputRecord.builder().id(RECORD_ID1).errors(Collections.EMPTY_LIST).build();
        ValidationOutputRecord validationOutputRecord2 = ValidationOutputRecord.builder().id(RECORD_ID2).errors(errors2).build();
        List<ValidationOutputRecord> expectedValidationOutputRecords = new ArrayList<>();
        expectedValidationOutputRecords.add(validationOutputRecord1);
        expectedValidationOutputRecords.add(validationOutputRecord2);

        List<ValidationOutputRecord> response = this.sut.validateUserAccessToRecords(recordsMetadata, OperationType.view);
        assertEquals(expectedValidationOutputRecords, response);
    }

    private String getExpectedRequestBody(String operation) {
        return "{\"input\":{\"operation\":\"" + operation + "\",\"token\":\"testtoken\",\"xuserid\":\"user-id\"," +
                "\"datapartitionid\":\"tenant1\",\"records\":[{\"kind\":\"tenant1:test:kind:1.0.0\"," +
                "\"acls\":{\"viewers\":[\"data.email1@tenant1.gmail.com\",\"data.test@tenant1.gmail.com\"]," +
                "\"owners\":[\"data.email1@tenant1.gmail.com\",\"data.test@tenant1.gmail.com\"]}}," +
                "{\"kind\":\"tenant1:test:crazy:2.0.2\",\"acls\":{\"viewers\":[\"data.email1@tenant1.gmail.com\"," +
                "\"data.test@tenant1.gmail.com\"],\"owners\":[\"data.email1@tenant1.gmail.com\"," +
                "\"data.test@tenant1.gmail.com\"]}}]}}";
    }

}
