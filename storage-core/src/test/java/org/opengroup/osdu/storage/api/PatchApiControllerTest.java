// Copyright 2017-2023, Schlumberger
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

package org.opengroup.osdu.storage.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.storage.StorageRole;
import org.opengroup.osdu.storage.model.PatchRecordsRequestModel;
import org.opengroup.osdu.storage.model.RecordQueryPatch;
import org.opengroup.osdu.storage.response.PatchRecordsResponse;
import org.opengroup.osdu.storage.util.RecordConstants;
import org.opengroup.osdu.storage.validation.ValidationDoc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(controllers = PatchApi.class)
@ComponentScan("org.opengroup.osdu")
public class PatchApiControllerTest extends ApiTest<PatchRecordsRequestModel> {

    private final ObjectMapper mapper = new ObjectMapper();
    private Gson gson = new Gson();

    @BeforeEach
    public void setup() {
        Mockito.when(dpsHeaders.getUserEmail()).thenReturn("a@b");
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("opendes");
        Mockito.when(dpsHeaders.getAuthorization()).thenReturn("auth");
        Mockito.when(collaborationFeatureFlag.isFeatureEnabled(RecordConstants.COLLABORATIONS_FEATURE_NAME)).thenReturn(false);
    }

    @Test
    public void should_returnUnauthorized_when_patchRecordsWithViewerPermissions() throws Exception {
        setupAuthorization(StorageRole.VIEWER);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123"})).build();
        ResultActions result = sendRequest(getRequestPayload(getValidInputJson(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isForbidden()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(403, appError.getCode());
        assertEquals("Access denied", appError.getReason());
        assertEquals("The user is not authorized to perform this action", appError.getMessage());
    }

    @Test
    public void should_return400_when_patchRecordsAndOperationOtherThanAddRemoveOrReplace() throws Exception {
        setupAuthorization(StorageRole.CREATOR);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123"})).build();
        ResultActions result = sendRequest(getRequestPayload(getInValidInputJsonBadOp(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(400, appError.getCode());
        assertEquals("Validation failed", appError.getReason());
        assertEquals(ValidationDoc.INVALID_PATCH_OPERATION, appError.getMessage());
    }

    @Test
    public void should_return400_when_patchRecordsAndUpdatingMetadataOtherThanAclTagsAncestryLegalOrKind() throws Exception {
        setupAuthorization(StorageRole.ADMIN);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{"opendes:npe:123"})).build();
        ResultActions result = sendRequest(getRequestPayload(getInValidInputJsonBadPath(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(400, appError.getCode());
        assertEquals("Validation failed", appError.getReason());
        assertEquals(ValidationDoc.INVALID_PATCH_PATH_START, appError.getMessage());
    }

    @Test
    public void should_return400_when_patchingMoreThan100Records() throws Exception {
        setupAuthorization(StorageRole.ADMIN);
        RecordQueryPatch recordQueryPatch = getRecordQueryPatchFor101Records();
        ResultActions result = sendRequest(getRequestPayload(getValidInputJson(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(400, appError.getCode());
        assertEquals("Validation failed", appError.getReason());
        assertEquals(ValidationDoc.PATCH_RECORDS_MAX, appError.getMessage());
    }

    @Test
    public void should_return400_when_patchingZeroRecords() throws Exception {
        setupAuthorization(StorageRole.ADMIN);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(Arrays.asList(new String[]{})).build();
        ResultActions result = sendRequest(getRequestPayload(getValidInputJson(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(400, appError.getCode());
        assertEquals("Validation failed", appError.getReason());
        assertEquals(ValidationDoc.RECORD_ID_LIST_NOT_EMPTY, appError.getMessage());
    }

    @Test
    public void should_return400_when_patchingNullRecords() throws Exception {
        setupAuthorization(StorageRole.ADMIN);
        RecordQueryPatch recordQueryPatch = RecordQueryPatch.builder().ids(null).build();
        ResultActions result = sendRequest(getRequestPayload(getValidInputJson(), recordQueryPatch));
        MockHttpServletResponse response = result.andExpect(MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse();
        AppError appError = gson.fromJson(response.getContentAsString(), AppError.class);
        assertEquals(400, appError.getCode());
        assertEquals("Validation failed", appError.getReason());
        assertEquals(ValidationDoc.RECORD_ID_LIST_NOT_EMPTY, appError.getMessage());
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
                .failedRecordIds(Arrays.asList(new String[]{"opendes:npe:123"}))
                .build();
        Mockito.when(patchRecordsService.patchRecords(eq(recordIds), any(JsonPatch.class), eq("a@b"), eq(Optional.empty()))).thenReturn(response);
        ResultActions result = sendRequest(getRequestPayloadMultipleIds(recordQueryPatch, getValidInputJson()));
        MockHttpServletResponse mockResponse = result.andExpect(MockMvcResultMatchers.status().isPartialContent()).andReturn().getResponse();
        PatchRecordsResponse recordsResponse = gson.fromJson(mockResponse.getContentAsString(), PatchRecordsResponse.class);
        assertEquals(3, recordsResponse.getRecordIds().size());
        assertEquals(1, recordsResponse.getFailedRecordIds().size());
        assertEquals(0, recordsResponse.getNotFoundRecordIds().size());
    }

    private PatchRecordsRequestModel getRequestPayload(String inputJson, RecordQueryPatch recordQueryPatch) throws Exception {
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
                "        \"path\": \"/tags/tag3\",\n" +
                "        \"value\": \"value3\"\n" +
                "    }\n" +
                "]";
    }

    private String getInValidInputJsonBadOp() {
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

    private String getInValidInputJsonBadPath() {
        return "[\n" +
                "    {\n" +
                "        \"op\": \"add\",\n" +
                "        \"path\": \"/other\",\n" +
                "        \"value\": {\n" +
                "            \"tag3\" : \"value3\"\n" +
                "        }\n" +
                "    }\n" +
                "]";
    }

    private RecordQueryPatch getRecordQueryPatchFor101Records() {
        List<String> recordIds = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            recordIds.add("opendes:npe:123" + i);
        }
        return RecordQueryPatch.builder().ids(recordIds).build();
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
