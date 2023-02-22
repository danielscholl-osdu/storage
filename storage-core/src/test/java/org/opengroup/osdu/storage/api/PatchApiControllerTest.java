package org.opengroup.osdu.storage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.storage.RecordQuery;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
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

@RunWith(SpringRunner.class)
@WebMvcTest(controllers = PatchApi.class)
@ComponentScan("org.opengroup.osdu")
public class PatchApiControllerTest extends ApiTest<PatchRecordsRequestModel> {

    private final ObjectMapper mapper = new ObjectMapper();
    private Gson gson = new Gson();

    @Ignore
    @Test
    public void should_returnUnauthorized_when_patchRecordsWithViewerPermissions() throws Exception {
        setupAuthorization(StorageRole.VIEWER);
        ResultActions result = sendPatchRequest();
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isUnauthorized()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        Assert.assertEquals(401, appError.getCode());
        Assert.assertEquals("Unauthorized", appError.getReason());
        Assert.assertEquals("The user is not authorized to perform this action", appError.getMessage());
    }

    @Test
    public void should_return400_when_patchRecordsAndOperationOtherThanAddRemoveOrReplace() {

    }

    @Test
    public void should_return400_when_patchRecordsAndUpdatingMetadataOtherThanAclTagsAncestryLegalOrKind() {

    }

    @Test
    public void should_return400_when_patchRecordsNoOperation() {

    }

    @Test
    public void should_return200_when_patchRecordsIsSuccess() {

    }

    @Test
    public void should_return206_when_patchRecordsIsPartialSuccess() {

    }

    @Test
    public void should_returnIdWithVersion_when_patchRecordsOnlyDataIsUpdated() {

    }

    @Test
    public void should_returnIdWithVersion_when_patchRecordsDataAndMetadataIsUpdated() {

    }

    @Test
    public void should_returnIdWithoutVersion_when_patchRecordsDataIsNotUpdated() {

    }

    @Test
    public void should_return200_when_patchRecordsIsSuccessWithCollaborationContext() {

    }

    private ResultActions sendPatchRequest() throws Exception {
        RecordQuery recordQuery = RecordQuery.builder().ids(Arrays.asList(new String[]{"opendes:npe:123"})).build();
        PatchRecordsRequestModel requestPayload = PatchRecordsRequestModel.builder()
                .query(recordQuery)
                .ops(getJsonPatchFromJsonString(getGenericInputJson()))
                .build();

        return sendRequest(requestPayload);
    }

    private JsonPatch getJsonPatchFromJsonString(String jsonString) throws IOException {
        final InputStream in = new ByteArrayInputStream(jsonString.getBytes());
        return mapper.readValue(in, JsonPatch.class);
    }

    private String getGenericInputJson() {
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

    @Override
    protected HttpMethod getHttpMethod() {
        return HttpMethod.PATCH;
    }

    @Override
    protected String getUriTemplate() {
        return "/records";
    }
}
