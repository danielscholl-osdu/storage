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

package org.opengroup.osdu.storage.simpledelivery;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.opengroup.osdu.storage.util.AzureTestUtils;
import org.opengroup.osdu.storage.util.HeaderUtils;
import org.opengroup.osdu.storage.util.TenantUtils;
import org.opengroup.osdu.storage.util.TestUtils;
import org.apache.http.HttpStatus;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;
import org.opengroup.osdu.storage.util.LegalTagUtils;
import static org.junit.Assert.assertEquals;
import static org.opengroup.osdu.storage.util.LegalTagUtils.createRandomName;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestGetFilesSignedUrlSuccessful {

    private static AzureTestUtils azureTestUtils = new AzureTestUtils();

    private static LegalTagUtils legalTagUtils = new LegalTagUtils();

    static final String RECORD_ID = TenantUtils.getTenantName() + ":id:" + System.currentTimeMillis();

    static final String SCHEMA = TenantUtils.getTenantName() + ":storage:deliveryinttest:1.0.0"
            + System.currentTimeMillis();

    static final String LEGAL_TAG = createRandomName() + "-Delivery";

    @BeforeClass
    public static void setup() throws Exception {
        DeliveryTestUtils.generateFileNames();
        DeliveryTestUtils.generateContainerNames();
        DeliveryTestUtils.generateTestBlobs();

        String token = azureTestUtils.getToken();

        legalTagUtils.create(LEGAL_TAG, token);

        String schemaJsonBody = DeliveryTestUtils.validPostBody(SCHEMA);
        ClientResponse response = TestUtils.send("schemas", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), schemaJsonBody, "");
        assertEquals(HttpStatus.SC_CREATED, response.getStatus());
        assertEquals("", response.getEntity(String.class));

        response = TestUtils.send("schemas/" + SCHEMA, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        assertEquals(HttpStatus.SC_OK, response.getStatus());

        for (String fileName : DeliveryTestUtils.pathMap.keySet()) {
            boolean isContainer = false;
            if (fileName.contains("container")) {
                isContainer = true;
            }
            String recordJsonBody = DeliveryTestUtils.createJsonRecord(RECORD_ID, fileName, SCHEMA, LEGAL_TAG, DeliveryTestUtils.pathMap.get(fileName), isContainer);
            response = TestUtils.send("records", "PUT", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), recordJsonBody, "");
            assertEquals(201, response.getStatus());
            assertEquals("application/json; charset=UTF-8", response.getType().toString());
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        String token = azureTestUtils.getToken();
        ClientResponse response;
        for (String fileName : DeliveryTestUtils.pathMap.keySet()) {
            response = TestUtils.send("records/" + RECORD_ID + "-"+ fileName, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
            assertEquals(204, response.getStatus());
        }

        response = TestUtils.send("schemas/" + SCHEMA, "DELETE", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        assertEquals(HttpStatus.SC_NO_CONTENT, response.getStatus());

        response = TestUtils.send("schemas/" + SCHEMA, "GET", HeaderUtils.getHeaders(TenantUtils.getTenantName(), token), "", "");
        assertEquals(HttpStatus.SC_NOT_FOUND, response.getStatus());

        legalTagUtils.delete(LEGAL_TAG, token);
        DeliveryTestUtils.deleteTestBlobs();
    }

//    @Test
//    public void should_GetResults_a_2Urls_when_requestingContainerSRNs() throws Exception {
//        Integer expectedUnprocessed = 0;
//        Integer expectedProcessed = 2;
//        JsonArray srns = new JsonArray();
//
//        srns.add(String.format("srn:file/ovds:%s", DeliveryTestUtils.CONTAINER_NAMES[0].hashCode()));
//        srns.add(String.format("srn:file/ovds:%s", DeliveryTestUtils.CONTAINER_NAMES[1].hashCode()));
//
//        JsonObject srnJsonObject = new JsonObject();
//        srnJsonObject.add("srns", srns);
//
//        String jsonBody = srnJsonObject.toString();
//        ClientResponse response;
//
//        for (int i = 0; ; i++) {
//            response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
//            if(DeliveryTestUtils.IndexedDocumentsExist(response, expectedProcessed)) {
//                break;
//            } else {
//                System.out.println("Sleeping for 15 seconds before GET delivery/GetFileSignedURL");
//                Thread.sleep(15000);
//                if (i > 12) {
//                    throw new AssertionError("Failed to get processed docs from delivery/GetFileSignedURL in under 3 minutes");
//                }
//            }
//        }
//        response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
//        DeliveryTestUtils.assertEqualsResponse(response, expectedProcessed, expectedUnprocessed);
//    }

//    @Test
//    public void should_GetResults_a_3Urls_when_requestingSRNsThatHave3Entries() throws Exception {
//        Integer expectedUnprocessed = 2;
//        Integer expectedProcessed = 3;
//
//        JsonArray srns = new JsonArray();
//        srns.add("invalidSRN1");
//        srns.add("invalidSRN2");
//        srns.add(String.format("srn:type:file/csv:%s:1", DeliveryTestUtils.FILE_NAMES[0].hashCode()));
//        srns.add(String.format("srn:type:file/csv:%s:1", DeliveryTestUtils.FILE_NAMES[1].hashCode()));
//        srns.add(String.format("srn:type:file/csv:%s:1", DeliveryTestUtils.FILE_NAMES[2].hashCode()));
//
//        JsonObject srnJsonObject = new JsonObject();
//        srnJsonObject.add("srns", srns);
//
//        String jsonBody = srnJsonObject.toString();
//        ClientResponse response;
//
//        for (int i = 0; ; i++) {
//            response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
//            if(DeliveryTestUtils.IndexedDocumentsExist(response, expectedProcessed)) {
//                break;
//            } else {
//                System.out.println("Sleeping for 15 seconds before GET delivery/GetFileSignedURL");
//                Thread.sleep(15000);
//                if (i > 12) {
//                    throw new AssertionError("Failed to get processed docs from delivery/GetFileSignedURL in under 3 minutes");
//                }
//            }
//        }
//        response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
//        DeliveryTestUtils.assertEqualsResponse(response, expectedProcessed, expectedUnprocessed);
//    }

//    @Test
//    public void should_GetResults_b_1Urls_when_requestingSRNsThatHave1Entry() throws Exception {
//        Integer expectedUnprocessed = 0;
//        Integer expectedProcessed = 1;
//
//        JsonArray srns = new JsonArray();
//        srns.add(String.format("srn:type:file/csv:%s:1", DeliveryTestUtils.FILE_NAMES[0].hashCode()));
//
//        JsonObject srnJsonObject = new JsonObject();
//        srnJsonObject.add("srns", srns);
//
//        String jsonBody = srnJsonObject.toString();
//
//        ClientResponse response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
//        DeliveryTestUtils.assertEqualsResponse(response, expectedProcessed, expectedUnprocessed);
//    }

    @Test
    public void should_GetResults_c_0Urls_when_requestingSRNsThatHave0Entries() throws Exception {
        Integer expectedUnprocessed = 2;
        Integer expectedProcessed = 0;

        JsonArray srns = new JsonArray();
        srns.add("invalidSRN1");
        srns.add("invalidSRN2");

        JsonObject srnJsonObject = new JsonObject();
        srnJsonObject.add("srns", srns);

        String jsonBody = srnJsonObject.toString();

        ClientResponse response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonBody, "");
        DeliveryTestUtils.assertEqualsResponse(response, expectedProcessed, expectedUnprocessed);
    }
}
