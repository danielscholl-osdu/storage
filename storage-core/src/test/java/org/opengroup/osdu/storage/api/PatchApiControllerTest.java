package org.opengroup.osdu.storage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.StringConstants;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = PatchApi.class)
@ComponentScan("org.opengroup.osdu")
public class PatchApiControllerTest extends ApiTest<PatchRecordsRequestModel> {

    private final ObjectMapper mapper = new ObjectMapper();
    private Gson gson = new Gson();

    @Before
    public void setup() {
        Mockito.when(dpsHeaders.getUserEmail()).thenReturn("a@b");
        Mockito.when(collaborationFeatureFlag.isFeatureEnabled(StringConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(false);
    }

    @Test
    public void should_returnUnauthorized_when_patchRecordsWithViewerPermissions() throws Exception {
        setupAuthorization(StorageRole.VIEWER);
        ResultActions result = sendRequest(getRequestPayload(getValidInputJson()));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isForbidden()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        Assert.assertEquals(403, appError.getCode());
        Assert.assertEquals("Access denied", appError.getReason());
        Assert.assertEquals("The user is not authorized to perform this action", appError.getMessage());
    }

    @Test
    public void should_return400_when_patchRecordsAndOperationOtherThanAddRemoveOrReplace() throws Exception {
        setupAuthorization(StorageRole.CREATOR);
        ResultActions result = sendRequest(getRequestPayload(getInvalidInputJsonOp()));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        Assert.assertEquals(400, appError.getCode());
        Assert.assertEquals("Validation failed", appError.getReason());
        Assert.assertEquals(ValidationDoc.INVALID_PATCH_OPERATION, appError.getMessage());
    }

    @Test
    public void should_return400_when_patchRecordsAndUpdatingMetadataOtherThanAclTagsAncestryLegalOrKind() throws Exception {
        setupAuthorization(StorageRole.ADMIN);
        ResultActions result = sendRequest(getRequestPayload(getInvalidInputJsonPath()));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        Assert.assertEquals(400, appError.getCode());
        Assert.assertEquals("Validation failed", appError.getReason());
        Assert.assertEquals(ValidationDoc.INVALID_PATCH_PATH, appError.getMessage());
    }

    @Test
    public void should_return200_when_patchRecordsIsSuccess() throws Exception {
        setupAuthorization(StorageRole.CREATOR);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123", "opendes:npe:124"})).build();
        List<String> recordIds = recordQueryPatch.getIds();
        PatchRecordsResponse response = PatchRecordsResponse.builder()
                .recordCount(2)
                .recordIds(recordIds)
                .build();
        Mockito.when(patchRecordsService.patchRecords(eq(recordIds), any(JsonPatch.class), eq("a@b"), eq(Optional.empty()))).thenReturn(response);
        ResultActions result = sendRequest(getRequestPayloadMultipleIds(recordQueryPatch, getValidInputJson()));
        MockHttpServletResponse mockResponse = result.andExpect(MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        assertEquals(200, mockResponse.getStatus());
        PatchRecordsResponse recordsResponse = gson.fromJson(mockResponse.getContentAsString(), PatchRecordsResponse.class);
        assertEquals(2, recordsResponse.getRecordIds().size());
        assertEquals(0, recordsResponse.getFailedRecordIds().size());
        assertEquals(0, recordsResponse.getNotFoundRecordIds().size());
    }

    @Test
    public void should_return206_when_patchRecordsIsPartialSuccess() throws Exception {
        setupAuthorization(StorageRole.CREATOR);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123", "opendes:npe:124", "opendes:npe:125"})).build();
        List<String> recordIds = recordQueryPatch.getIds();
        PatchRecordsResponse response = PatchRecordsResponse.builder()
                .recordCount(3)
                .recordIds(recordIds)
                .failedRecordIds(Arrays.asList(new String[] {"opendes:npe:123"}))
                .build();
        Mockito.when(patchRecordsService.patchRecords(eq(recordIds), any(JsonPatch.class), eq("a@b"), eq(Optional.empty()))).thenReturn(response);
        ResultActions result = sendRequest(getRequestPayloadMultipleIds(recordQueryPatch, getValidInputJson()));
        MockHttpServletResponse mockResponse = result.andExpect(MockMvcResultMatchers.status().isPartialContent()).andReturn().getResponse();
        PatchRecordsResponse recordsResponse = gson.fromJson(mockResponse.getContentAsString(), PatchRecordsResponse.class);
        assertEquals(3, recordsResponse.getRecordIds().size());
        assertEquals(1, recordsResponse.getFailedRecordIds().size());
        assertEquals(0, recordsResponse.getNotFoundRecordIds().size());
    }

    private PatchRecordsRequestModel getRequestPayload(String inputJson) throws Exception {
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123"})).build();
        PatchRecordsRequestModel requestPayload = PatchRecordsRequestModel.builder()
                .query(recordQueryPatch)
                .ops(getJsonPatchFromJsonString(inputJson))
                .build();
        return requestPayload;
    }

    private PatchRecordsRequestModel getRequestPayloadMultipleIds(RecordQueryPatch recordQueryPatch, String inputJson) throws Exception {
        PatchRecordsRequestModel requestPayload = PatchRecordsRequestModel.builder()
                .query(recordQueryPatch)
                .ops(getJsonPatchFromJsonString(inputJson))
                .build();
        return requestPayload;
    }

    private JsonPatch getJsonPatchFromJsonString(String jsonString) throws IOException {
        final InputStream in = new ByteArrayInputStream(jsonString.getBytes());
        return mapper.readValue(in, JsonPatch.class);
    }

    private String getValidInputJson() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"add\",\n" +
                "        \"path\": \"/tags\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

    private String getInvalidInputJsonOp() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"test\",\n" +
                "        \"path\": \"/tags\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

    private String getInvalidInputJsonPath() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"replace\",\n" +
                "        \"path\": \"/other\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

    @Override
    protected HttpMethod getHttpMethod() {
        return HttpMethod.PATCH;
    }

    @Override
    protected String getUriTemplate() {
        return "/records";
    }
}
