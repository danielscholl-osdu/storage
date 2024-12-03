// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.Replay;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.http.HttpStatus;

import com.google.gson.Gson;
import org.junit.Test;
import org.opengroup.osdu.storage.model.ReplayStatusResponseHelper;
import org.opengroup.osdu.storage.records.RecordsApiAcceptanceTests;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.opengroup.osdu.storage.util.DummyRecordsHelper;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestBase;
import org.opengroup.osdu.storage.util.TestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public abstract class ReplayEndpointsTests extends TestBase {
    protected static String LEGAL_TAG_NAME = LegalTagUtils.createRandomName();

    protected static final String INVALID_KIND = TenantUtils.getTenantName() + ":ds:1.0."
            + System.currentTimeMillis();

    public static void classSetup(String token) throws Exception {

        LegalTagUtils.create(LEGAL_TAG_NAME, token);
    }

    public static void classTearDown(String token) throws Exception {

        LegalTagUtils.delete(LEGAL_TAG_NAME, token);
    }

    @Test
    public void should_return_400_when_givenNoOperationNameIsNotInRequest() throws Exception {

        String requestBody = ReplayUtils.createJsonEmpty();
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        assertEquals(400, response.getCode());
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals(400, response.getCode());
        assertEquals("Operation field is required. The valid operations are: 'replay', 'reindex'.", actualErrorMessage);
    }

    @Test
    public void should_return_400_when_givenKindIsEmpty() throws Exception {

        String requestBody = ReplayUtils.createJsonWithKind("reindex", new ArrayList<>());
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals(400, response.getCode());
        assertEquals("Currently restricted to a single valid kind.", actualErrorMessage);
    }

    @Test
    public void should_return_400_when_givenKindSizeIsGreaterDenOne() throws Exception {

        List<String> kindList = new ArrayList<>();
        kindList.add(getKind());
        kindList.add(getKind());

        String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals(400, response.getCode());
        assertEquals("Currently restricted to a single valid kind.", actualErrorMessage);
    }

    @Test
    public void Should_return_400_when_givenInvalidKind() throws Exception {

        List<String> kindList = new ArrayList<>();
        kindList.add(INVALID_KIND);
        String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals(400, response.getCode());
        assertEquals("The requested kind does not exist.", actualErrorMessage);
    }

    @Test
    public void Should_return_400_when_givenInvalidOperationName() throws Exception {

        String requestBody = ReplayUtils.createJsonWithOperationName("invalidOperation");
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals(400, response.getCode());
        assertEquals("Not a valid operation. The valid operations are: [reindex, replay]", actualErrorMessage);
    }

    @Test
    public void should_return_200_GivenReplayAll() throws Exception {

        if (configUtils != null && configUtils.getIsTestReplayAllEnabled()) {

            String kind_1 = getKind();
            String kind_2 = getKind();
            List<String> givenKindList = Arrays.asList(kind_1, kind_2);
            List<String> totalRecordIds = this.createTestRecordForGivenCapacityAndKinds(500, 100, givenKindList);

            DummyRecordsHelper dummyRecordsHelper = new DummyRecordsHelper();

            CloseableHttpResponse response = TestUtils.send("query/kinds", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "?limit=10");
            assertEquals(HttpStatus.SC_OK, response.getCode());
            DummyRecordsHelper.QueryResultMock responseObject = dummyRecordsHelper.getQueryResultMockFromResponse(response);
            List<String> kindList = new ArrayList<>(Arrays.asList(responseObject.results));
            kindList.add(kind_1);
            kindList.add(kind_2);

            performValidationBeforeOrAfterReplay(kindList, givenKindList, "*:*:*:*", 2000);
            String requestBody = ReplayUtils.createJsonWithOperationName("reindex");
            ReplayStatusResponseHelper replayStatusResponseHelper = this.performReplay(requestBody);
            assertEquals("reindex", replayStatusResponseHelper.getOperation());

            assertNull(replayStatusResponseHelper.getFilter());
            assertEquals(1, replayStatusResponseHelper.getStatus().size());
            performValidationBeforeOrAfterReplay(kindList, givenKindList, "*:*:*:*", 2000);
        }
    }

    @Test(timeout = 2000)
    public void should_return_200_givenSingleKind() throws Exception {

        if (configUtils != null && configUtils.getIsTestReplayAllEnabled()) {

            String kind_1 = getKind();
            List<String> kindList = new ArrayList<>();
            kindList.add(kind_1);
            List<String> ids = this.createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

            performValidationBeforeOrAfterReplay(kindList, kindList, kind_1, 1);
            String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
            ReplayStatusResponseHelper replayStatusResponseHelper = performReplay(requestBody);

            assertEquals("reindex", replayStatusResponseHelper.getOperation());
            assertEquals(1, replayStatusResponseHelper.getFilter().getKinds().size());
            assertEquals(1, replayStatusResponseHelper.getStatus().size());
            assertEquals(kind_1, replayStatusResponseHelper.getFilter().getKinds().get(0));

            performValidationBeforeOrAfterReplay(kindList, kindList, kind_1, 1);
            deleteRecords(ids);
        }
    }

    public List<String> createTestRecordForGivenCapacityAndKinds(int n, int factor, List<String> kinds) throws Exception {

        int totalRecordCount = n * kinds.size();
        long startTime = System.currentTimeMillis();
        List<String> totalIds = new ArrayList<>();
        for (String kind : kinds) {
            int counter = n;
            while (counter > 0) {
                List<String> listIds = create_N_TestRecordForGivenKind(factor, kind);
                totalIds = Stream.concat(totalIds.stream(), listIds.stream()).collect(Collectors.toList());
                counter -= factor;
                Thread.sleep(1000);
            }

        }

        Thread.sleep(40000);
        return totalIds;
    }

    @Test
    public void should_return_400_when_givenEmptyJSONIsSent() throws Exception {

        String requestBody = ReplayUtils.createJsonEmpty();
        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
        assertEquals(400, response.getCode());
    }

    protected List<String> create_N_TestRecordForGivenKind(int n, String kind) throws Exception {

        String json = "";
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id1 = TenantUtils.getTenantName() + ":inttest:" + System.currentTimeMillis() + i;
            json += RecordsApiAcceptanceTests.singleEntityBody(id1, "ash ketchum", kind, LEGAL_TAG_NAME);
            if (i != n - 1) {
                json += ",";
            }
            ids.add(id1);
        }

        json = "[" + json + "]";

        CloseableHttpResponse response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), json, "");

        String responseJson = EntityUtils.toString(response.getEntity());
        assertEquals(201, response.getCode());
        Gson gson = new Gson();
        DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(
                responseJson,
                DummyRecordsHelper.CreateRecordResponse.class
                                                                      );
        assertEquals(n, result.recordCount);
        assertEquals(n, result.recordIds.length);
        assertEquals(n, result.recordIdVersions.length);

        return ids;
    }

    private void performValidationBeforeOrAfterReplay(List<String> kinds, List<String> givenKindList, String kindType, int totalReplayAllRecord) throws Exception {

        long startTime = System.currentTimeMillis();
        CloseableHttpResponse response = null;

        int initialRecordCount = 0;
        int countNoOfAPICalls = 0;
        while ((initialRecordCount = getIndexedRecordCount(givenKindList)) != totalReplayAllRecord) {
            if (countNoOfAPICalls > 10)
                fail();

            Thread.sleep(configUtils.getTimeoutForReplay());
            countNoOfAPICalls++;
        }

        assertEquals(totalReplayAllRecord, initialRecordCount);

        System.out.println("Total count for Kind " + kindType + " is " + initialRecordCount);

        for (String kind : kinds) {
            response = TestUtils.send(ReplayUtils.getIndexerUrl(), "index?kind=" + kind, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
            Thread.sleep(1000);
        }

        int countOfRecord = getIndexedRecordCount(givenKindList);
        System.out.println("Total count for Kind " + kindType + " after deletion is " + countOfRecord);
        assertEquals(0, countOfRecord);

        System.out.println("The end time for performValidationBeforeOrAfterReplay for KindType " + kindType + "is " + (System.currentTimeMillis() - startTime));
    }

    private ReplayStatusResponseHelper performReplay(String requestBody) throws Exception {

        CloseableHttpResponse response = TestUtils.send("replay", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");

        if (response.getCode() == 500)
            System.out.println("Error in replay call  " + ReplayUtils.getFieldFromResponse(response, "message"));

        assertEquals(202, response.getCode());

        String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
        response = TestUtils.send("replay/status/", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", replayId);

        ReplayStatusResponseHelper replayStatusResponseHelper = ReplayUtils.getConvertedReplayStatusResponseFromResponse(response);
        System.out.println("Total Number of Record to be Processed for Replay " + replayStatusResponseHelper.getTotalRecords());

        int countNoOfAPICalls = 0;

        while (!replayStatusResponseHelper.getOverallState().equals("COMPLETED")) {
            assertNotEquals("FAILED", replayStatusResponseHelper.getOverallState());
            response = TestUtils.send("replay/status/", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", replayId);
            replayStatusResponseHelper = ReplayUtils.getConvertedReplayStatusResponseFromResponse(response);
            if (replayStatusResponseHelper.getStatus() != null && !replayStatusResponseHelper.getStatus().isEmpty())
                System.out.println("Number of Record to be Processed for Replay  " + replayStatusResponseHelper.getStatus().get(0).getProcessedRecords());

            if (countNoOfAPICalls > 10)
                 fail();

            Thread.sleep(configUtils.getTimeoutForReplay());
            countNoOfAPICalls++;
        }

        assertEquals(replayId, replayStatusResponseHelper.getReplayId());
        return replayStatusResponseHelper;
    }

    protected void deleteRecords(List<String> ids) {

        long startTime = System.currentTimeMillis();

        ids.parallelStream().forEach((id) -> {
            try {
                TestUtils.send("records/" + id, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
            } catch (Exception e) {
            }
        });

        System.out.println("The totalTime for delete Records for size " + ids.size() + "is " + (System.currentTimeMillis() - startTime));
    }

    private int getIndexedRecordCount(List<String> kinds) throws Exception {

        int recordCountIndexed = 0;
        for (String kind : kinds) {
            String requestBody = ReplayUtils.getSearchCountQueryForKind(kind);
            CloseableHttpResponse response = TestUtils.send(ReplayUtils.getSearchUrl(), "query", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), requestBody, "");
            recordCountIndexed += Integer.parseInt(ReplayUtils.getFieldFromResponse(response, "totalCount"));

        }
        return recordCountIndexed;
    }

    @Test
    public void should_return_400_when_givenInvalidReplayID() throws Exception {

        CloseableHttpResponse response = TestUtils.send("replay/status/", "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "1234");
        String actualErrorMessage = ReplayUtils.getFieldFromResponse(response, "message");
        assertEquals("The replay ID 1234 is invalid.", actualErrorMessage);
        assertEquals(404, response.getCode());
    }

    public static String getKind() throws InterruptedException {

        Thread.sleep(1);
        return TenantUtils.getTenantName() + ":ds:inttest:1.0." + System.nanoTime();
    }
}
