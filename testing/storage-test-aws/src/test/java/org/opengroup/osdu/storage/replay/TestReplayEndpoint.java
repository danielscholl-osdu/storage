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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opengroup.osdu.storage.Replay.ReplayEndpointsTests;
import org.opengroup.osdu.storage.model.ReplayStatusResponseHelper;
import org.opengroup.osdu.storage.util.AWSTestUtils;
import org.opengroup.osdu.storage.util.ConfigUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.ReplayUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;

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
            ReplayStatusResponseHelper reindexResponse = performReplay(reindexRequestBody);
            assertEquals("reindex", reindexResponse.getOperation());
            assertEquals(kind, reindexResponse.getStatus().get(0).getKind());
            assertEquals("COMPLETED", reindexResponse.getOverallState());

            // Test replay operation
            String replayRequestBody = ReplayUtils.createJsonWithKind("replay", kindList);
            ReplayStatusResponseHelper replayResponse = performReplay(replayRequestBody);
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
            ReplayStatusResponseHelper status1 = ReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse1);
            assertEquals("COMPLETED", status1.getOverallState());
            
            CloseableHttpResponse statusResponse2 = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId2);
            ReplayStatusResponseHelper status2 = ReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse2);
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
            ReplayStatusResponseHelper statusHelper = ReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            
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
            ReplayStatusResponseHelper statusHelper = ReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
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
        int maxAttempts = 10;
        int attempt = 0;
        boolean completed = false;
        
        while (!completed && attempt < maxAttempts) {
            CloseableHttpResponse statusResponse = TestUtils.send("replay/status/", "GET", 
                HeaderUtils.getHeaders(TenantUtils.getTenantName(), testUtils.getToken()), 
                "", replayId);
            ReplayStatusResponseHelper statusHelper = ReplayUtils.getConvertedReplayStatusResponseFromResponse(statusResponse);
            
            if ("COMPLETED".equals(statusHelper.getOverallState()) || "FAILED".equals(statusHelper.getOverallState())) {
                completed = true;
            } else {
                attempt++;
                TimeUnit.SECONDS.sleep(2); // Wait 2 seconds before checking again
            }
        }
        
        // Verify operation completed
        assertTrue("Replay operation did not complete within the expected time", completed);
    }
}
