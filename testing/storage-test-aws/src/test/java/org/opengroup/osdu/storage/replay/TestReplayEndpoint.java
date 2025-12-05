// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.replay;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengroup.osdu.storage.Replay.ReplayEndpointsTests;
import org.opengroup.osdu.storage.model.AwsReplayStatusResponseHelper;
import org.opengroup.osdu.storage.util.*;
import org.opengroup.osdu.storage.util.AwsReplayUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class TestReplayEndpoint extends ReplayEndpointsTests {

    private static final AWSTestUtils awsTestUtils = new AWSTestUtils();

    @BeforeClass
    public static void classSetup() throws Exception {
        ReplayEndpointsTests.classSetup(awsTestUtils.getToken());
    }

    @AfterClass
    public static void classTearDown() throws Exception {
        ReplayEndpointsTests.classTearDown(awsTestUtils.getToken());
    }

    @Before
    @Override
    public void setup() throws Exception {
        this.testUtils = new AWSTestUtils();
        this.configUtils = new ConfigUtils("test.properties");
        assumeTrue(configUtils.isTestReplayEnabled());
    }

    @After
    @Override
    public void tearDown() throws Exception {
        this.testUtils = null;
    }

    /**
     * Override the test from the parent class that's timing out.
     * This implementation uses a more efficient approach specific to AWS.
     */
    @Override
    @Test
    public void should_return_200_givenSingleKind() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        // Create test records
        String kind = getKind();
        List<String> kindList = new ArrayList<>();
        kindList.add(kind);
        List<String> ids = this.createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

        try {
            // Test with reindex operation
            String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
            CloseableHttpResponse response = TestUtils.send("replay", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody, "");
            assertEquals(202, response.getCode());
            String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
            
            // Wait for operation to complete with a more efficient approach
            waitForReplayToComplete(replayId);
            
            // Check status response
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            AwsReplayStatusResponseHelper statusHelper = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            
            // Verify operation completed successfully
            assertEquals("reindex", statusHelper.getOperation());
            
            // Check filter safely - it might be null in AWS implementation
            assertNotNull("Status response should not be null", statusHelper);
            assertEquals("Operation should be completed", "COMPLETED", statusHelper.getOverallState());
            
            // Verify the kind is included in the response - either in filter or in status
            boolean kindFound = false;
            
            // Check if filter exists and contains our kind
            if (statusHelper.getFilter() != null && statusHelper.getFilter().getKinds() != null) {
                kindFound = statusHelper.getFilter().getKinds().contains(kind);
            }
            
            // If not found in filter, check in status list
            if (!kindFound && statusHelper.getStatus() != null && !statusHelper.getStatus().isEmpty()) {
                kindFound = statusHelper.getStatus().stream()
                    .anyMatch(status -> kind.equals(status.getKind()));
            }
            
            assertTrue("The test kind should be found in the response", kindFound);
        } finally {
            // Clean up
            deleteRecords(ids);
        }
    }

    /**
     * This version tests the replay functionality using a valid record with a known kind.
     */
    @Override
    @Test
    public void should_return_200_GivenReplayAll() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        String kind = "osdu:wks:dataset--File.Generic:1.0.0";
        List<String> kindList = new ArrayList<>();
        kindList.add(kind);

        String recordId = TenantUtils.getTenantName() + ":dataset--File.Generic:" + System.currentTimeMillis();
        String legalTagName = LEGAL_TAG_NAME;

        deleteIndexedRecordsForKind(kind);

        String recordJson = String.format(
                "[{" +
                        "\"id\":\"%s\"," +
                        "\"data\": {" +
                        "    \"Endian\": \"BIG\"," +
                        "    \"Name\": \"dummy\"," +
                        "    \"DatasetProperties.FileSourceInfo.FileSource\": \"\"," +
                        "    \"DatasetProperties.FileSourceInfo.PreloadFilePath\": \"\"" +
                        "}," +
                        "\"kind\": \"%s\"," +
                        "\"namespace\": \"osdu:wks\"," +
                        "\"legal\": {" +
                        "    \"legaltags\": [" +
                        "        \"%s\"" +
                        "    ]," +
                        "    \"otherRelevantDataCountries\": [" +
                        "        \"US\"" +
                        "    ]," +
                        "    \"status\": \"compliant\"" +
                        "}," +
                        "\"acl\": {" +
                        "    \"viewers\": [" +
                        "        \"%s\"" +
                        "    ]," +
                        "    \"owners\": [" +
                        "        \"%s\"" +
                        "    ]" +
                        "}," +
                        "\"type\": \"dataset--File.Generic\"," +
                        "\"version\": %d" +
                        "}]", recordId, kind, legalTagName, TestUtils.getAcl(), TestUtils.getAcl(), System.currentTimeMillis());

        CloseableHttpResponse createResponse = TestUtils.send(
                "records",
                "PUT",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()),
                recordJson,
                "");

        assertEquals(201, createResponse.getCode());

        String responseJson = EntityUtils.toString(createResponse.getEntity());
        Gson gson = new Gson();
        DummyRecordsHelper.CreateRecordResponse result = gson.fromJson(
                responseJson,
                DummyRecordsHelper.CreateRecordResponse.class
        );

        List<String> recordIds = Arrays.asList(result.recordIds);

        try {
            // Wait for records to be indexed
            int initialCount = waitForRecordsToBeIndexed(kind, 1, 60);
            
            // Verify our test records were created
            assertTrue("Test records for kind should be indexed", initialCount > 0);

            deleteIndexedRecordsForKind(kind);
            
            // Verify deletion was successful
            assertEquals("Indexed records for kind should be deleted", 0, getIndexedRecordCountForKind(kind));
            
            // Trigger replay all operation
            String requestBody = ReplayUtils.createJsonWithOperationName("reindex");
            CloseableHttpResponse response = TestUtils.send("replay", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody, "");
            assertEquals(202, response.getCode());
            String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
            
            // Wait for operation to complete
            waitForReplayToComplete(replayId);
            
            // Check status response
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            AwsReplayStatusResponseHelper statusHelper = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            
            // Verify operation completed successfully
            assertEquals("reindex", statusHelper.getOperation());
            assertEquals("COMPLETED", statusHelper.getOverallState());

            // Wait for records to be reindexed
            int finalCount = waitForRecordsToBeIndexed(kind, initialCount, 60);
            
            // Check that at least our test record was reindexed
            assertTrue("Records for kind should be reindexed (at least " + initialCount + " records)", finalCount >= initialCount);
            
        } finally {
            deleteRecords(recordIds);
        }
    }

    /**
     * Helper method to wait for a specific number of records to be indexed for a kind.
     * 
     * @param kind The kind to check
     * @param expectedCount The expected number of records
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return The actual count of records found
     */
    private int waitForRecordsToBeIndexed(String kind, int expectedCount, int timeoutSeconds) throws Exception {
        // Get initial count to handle cases where records might already exist
        int initialCount = getIndexedRecordCountForKind(kind);
        
        // If we already have the expected count, return immediately
        if (initialCount >= expectedCount) {
            return initialCount;
        }
        
        int currentCount = initialCount;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (timeoutSeconds * 1000);
        
        while (System.currentTimeMillis() < endTime) {
            currentCount = getIndexedRecordCountForKind(kind);
            
            if (currentCount >= expectedCount) {
                return currentCount;
            }
            
            // Wait 5 seconds before checking again
            TimeUnit.SECONDS.sleep(5);
        }
        
        // Return the current count even if we didn't reach the expected count
        return currentCount;
    }

    /**
     * Test that verifies both replay and reindex operations work with the consolidated SNS/SQS approach.
     * This test specifically checks that both operation types are processed correctly.
     */
    @Test
    public void should_process_both_replay_and_reindex_operations() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        // Create test records
        String kind = getKind();
        List<String> kindList = new ArrayList<>();
        kindList.add(kind);
        List<String> ids = this.createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

        try {
            // Test reindex operation
            String reindexRequestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
            AwsReplayStatusResponseHelper reindexResponse = performReplayWithTimeout(reindexRequestBody);
            assertEquals("reindex", reindexResponse.getOperation());
            assertEquals(kind, reindexResponse.getStatus().get(0).getKind());
            assertEquals("COMPLETED", reindexResponse.getOverallState());

            // Test replay operation
            String replayRequestBody = ReplayUtils.createJsonWithKind("replay", kindList);
            AwsReplayStatusResponseHelper replayResponse = performReplayWithTimeout(replayRequestBody);
            assertEquals("replay", replayResponse.getOperation());
            assertEquals(kind, replayResponse.getStatus().get(0).getKind());
            assertEquals("COMPLETED", replayResponse.getOverallState());
        } finally {
            // Clean up
            deleteRecords(ids);
        }
    }

    /**
     * Test that verifies concurrent replay operations can be processed correctly.
     * This tests the ability of the system to handle multiple replay operations simultaneously.
     */
    @Test
    public void should_handle_concurrent_replay_operations() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        // Create test records for two different kinds
        String kind1 = getKind();
        String kind2 = getKind();
        List<String> kind1List = new ArrayList<>();
        List<String> kind2List = new ArrayList<>();
        kind1List.add(kind1);
        kind2List.add(kind2);
        
        List<String> ids1 = this.createTestRecordForGivenCapacityAndKinds(1, 1, kind1List);
        List<String> ids2 = this.createTestRecordForGivenCapacityAndKinds(1, 1, kind2List);
        
        try {
            // Trigger first replay operation
            String requestBody1 = ReplayUtils.createJsonWithKind("reindex", kind1List);
            CloseableHttpResponse response1 = TestUtils.send("replay", "POST",
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody1, "");
            assertEquals(202, response1.getCode());
            String replayId1 = ReplayUtils.getFieldFromResponse(response1, "replayId");
            assertNotNull(replayId1);
            
            // Trigger second replay operation immediately
            String requestBody2 = ReplayUtils.createJsonWithKind("reindex", kind2List);
            CloseableHttpResponse response2 = TestUtils.send("replay", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody2, "");
            assertEquals(202, response2.getCode());
            String replayId2 = ReplayUtils.getFieldFromResponse(response2, "replayId");
            assertNotNull(replayId2);
            
            // Verify the replay IDs are different
            assertNotEquals(replayId1, replayId2);
            
            // Wait for both operations to complete
            waitForReplayToComplete(replayId1);
            waitForReplayToComplete(replayId2);
            
            // Verify both operations completed successfully
            CloseableHttpResponse statusResponse1 = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId1);
            AwsReplayStatusResponseHelper status1 = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse1);
            assertEquals("COMPLETED", status1.getOverallState());
            
            CloseableHttpResponse statusResponse2 = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId2);
            AwsReplayStatusResponseHelper status2 = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse2);
            assertEquals("COMPLETED", status2.getOverallState());
        } finally {
            // Clean up
            List<String> allIds = new ArrayList<>();
            allIds.addAll(ids1);
            allIds.addAll(ids2);
            deleteRecords(allIds);
        }
    }

    /**
     * Test that verifies the status response includes the correct operation attribute.
     * This test specifically checks that the operation attribute is correctly passed through
     * the consolidated SNS/SQS approach.
     */
    @Test
    public void should_include_operation_attribute_in_status() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        // Create test records
        String kind = getKind();
        List<String> kindList = new ArrayList<>();
        kindList.add(kind);
        List<String> ids = this.createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

        try {
            // Test with reindex operation
            String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
            CloseableHttpResponse response = TestUtils.send("replay", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody, "");
            assertEquals(202, response.getCode());
            String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
            
            // Wait for operation to complete
            waitForReplayToComplete(replayId);
            
            // Check status response
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            AwsReplayStatusResponseHelper statusHelper = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            
            // Verify operation attribute is correctly included
            assertEquals("reindex", statusHelper.getOperation());
            assertEquals(1, statusHelper.getStatus().size());
            assertEquals("COMPLETED", statusHelper.getStatus().get(0).getState());
        } finally {
            // Clean up
            deleteRecords(ids);
        }
    }

    /**
     * Test that verifies the response time for status queries is acceptable.
     * This test measures the response time for status queries to ensure they meet performance requirements.
     */
    @Test
    public void should_have_acceptable_status_query_performance() throws Exception {
        assumeTrue(configUtils != null && configUtils.getIsTestReplayAllEnabled());

        // Create test records
        String kind = getKind();
        List<String> kindList = new ArrayList<>();
        kindList.add(kind);
        List<String> ids = this.createTestRecordForGivenCapacityAndKinds(1, 1, kindList);

        try {
            // Trigger replay operation
            String requestBody = ReplayUtils.createJsonWithKind("reindex", kindList);
            CloseableHttpResponse response = TestUtils.send("replay", "POST", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                requestBody, "");
            assertEquals(202, response.getCode());
            String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
            
            // Wait for operation to complete
            waitForReplayToComplete(replayId);
            
            // Measure response time for status query
            long startTime = System.currentTimeMillis();
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            
            // Verify response time is acceptable (less than 2 seconds)
            assertTrue("Status query response time should be less than 2000ms but was " + responseTime + "ms", 
                responseTime < 2000);
            
            // Verify status response is correct
            assertEquals(200, statusResponse.getCode());
            AwsReplayStatusResponseHelper statusHelper = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            assertEquals("COMPLETED", statusHelper.getOverallState());
        } finally {
            // Clean up
            deleteRecords(ids);
        }
    }

    /**
     * Helper method to wait for a replay operation to complete.
     */
    private void waitForReplayToComplete(String replayId) throws Exception {
        int maxAttempts = 100;
        int attempt = 0;
        boolean completed = false;
        
        while (!completed && attempt < maxAttempts) {
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            AwsReplayStatusResponseHelper statusHelper = AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            System.out.println(statusHelper.getOverallState());
            
            if ("COMPLETED".equals(statusHelper.getOverallState()) || "FAILED".equals(statusHelper.getOverallState())) {
                completed = true;
            } else {
                attempt++;
                TimeUnit.SECONDS.sleep(2); // Wait 2 seconds before checking again
            }
        }
        
        // Verify operation completed
        assertTrue("Replay operation did not complete within the expected time. Current replayId: " + replayId, completed);
    }
    
    /**
     * A version of performReplay with a timeout to prevent test hangs
     */
    protected AwsReplayStatusResponseHelper performReplayWithTimeout(String requestBody) throws Exception {
        CloseableHttpResponse response = TestUtils.send("replay", "POST", 
            HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
            requestBody, "");

        if (response.getCode() == 500) {
            System.out.println("Error in replay call " + ReplayUtils.getFieldFromResponse(response, "message"));
        }

        assertEquals(202, response.getCode());

        String replayId = ReplayUtils.getFieldFromResponse(response, "replayId");
        
        // Wait for completion with timeout
        waitForReplayToComplete(replayId);
        
        // Get final status
        response = TestUtils.send("replay/status/", "GET", 
            HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
            "", replayId);

        
        return AwsReplayUtils.getConvertedReplayStatusResponseFromResponse(response);
    }
    
    /**
     * Helper method to get the indexed record count for a specific kind
     */
    private int getIndexedRecordCountForKind(String kind) throws Exception {
        String requestBody = ReplayUtils.getSearchCountQueryForKind(kind);
        CloseableHttpResponse response = TestUtils.send(ReplayUtils.getSearchUrl(), "query", "POST", 
            HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
            requestBody, "");
        
        String responseBody = EntityUtils.toString(response.getEntity());
        JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
        
        if (jsonResponse.has("totalCount") && !jsonResponse.get("totalCount").isJsonNull()) {
            return jsonResponse.get("totalCount").getAsInt();
        }
        
        return 0; // Return 0 if totalCount is not present or null
    }
    
    /**
     * Helper method to delete indexed records for a specific kind
     */
    private void deleteIndexedRecordsForKind(String kind) throws Exception {
        String encodedKind = URLEncoder.encode(kind, StandardCharsets.UTF_8);
        CloseableHttpResponse response = TestUtils.send(ReplayUtils.getIndexerUrl(), "index?kind=" + encodedKind, "DELETE",
            HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), "", "");
        assertTrue("Expected 200 or 404 status code", response.getCode() == 200 || response.getCode() == 404);
        
        // Wait for deletion to complete
        int attempts = 0;
        while (getIndexedRecordCountForKind(kind) > 0 && attempts < 5) {
            TimeUnit.SECONDS.sleep(1);
            attempts++;
        }
    }
}
