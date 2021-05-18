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

package org.opengroup.osdu.storage.util;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.storage.blob.*;

import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import org.apache.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class DeliveryTestUtils {

    private static String containerName = "delivery-int-test-temp";
    private static String storageAccount = getStorageAccount();
    final public static String[] FILE_NAMES = new String[] {"delivery-sample-1.txt", "delivery-sample-2.txt", "delivery-sample-3.txt"};
    final public static String[] CONTAINER_NAMES = new String[] {"delivery-sample-container-1", "delivery-sample-container-2"};
    public static HashMap<String, String> pathMap = new HashMap<>();
;
    public static void generateTestBlobs(){
        for(String fileName : FILE_NAMES) {
            DeliveryTestUtils.createBlob(pathMap.get(fileName));
        }
        for(String localContainer : CONTAINER_NAMES) {
            DeliveryTestUtils.createContainer(pathMap.get(localContainer));
        }
    }

    public static void generateFileNames() {
        for(String fileName : FILE_NAMES) {
            pathMap.put(fileName, generateBlobPath(storageAccount, containerName, fileName));
        }
    }

    public static void generateContainerNames() {
        for(String containerName : CONTAINER_NAMES) {
            pathMap.put(containerName, generateContainerPath(storageAccount, containerName));
        }
    }
    public static String createJsonRecord(String id, String fileName, String kind, String legalTag, String filePath, boolean isContainer) {

        JsonArray files = new JsonArray();
        String fileSrn = String.format("srn:type:file/csv:%s:1", fileName.hashCode());
        String contSrn = String.format("srn:file/ovds:%s", fileName.hashCode());
        files.add(isContainer ? contSrn : fileSrn);

        JsonObject data = new JsonObject();
        data.add("ResourceID", files);
        data.addProperty("Data.GroupTypeProperties.PreLoadFilePath", filePath);

        JsonObject acl = new JsonObject();
        JsonArray acls = new JsonArray();
        acls.add(TestUtils.getAcl());
        acl.add("viewers", acls);
        acl.add("owners", acls);

        JsonArray tags = new JsonArray();
        tags.add(legalTag);

        JsonArray ordcJson = new JsonArray();
        ordcJson.add("BR");

        JsonObject legal = new JsonObject();
        legal.add("legaltags", tags);
        legal.add("otherRelevantDataCountries", ordcJson);

        JsonObject record = new JsonObject();
        record.addProperty("id", id + "-" + fileName);
        record.addProperty("kind", kind);
        record.add("acl", acl);
        record.add("legal", legal);
        record.add("data", data);

        JsonArray records = new JsonArray();
        records.add(record);

        return records.toString();
    }

    public static String validPostBody(String kind) {

        JsonObject item1Ext = new JsonObject();

        JsonObject item1 = new JsonObject();
        item1.addProperty("path", "name");
        item1.addProperty("kind", "string");

        JsonObject item2 = new JsonObject();

        item2.addProperty("path", "Data.GroupTypeProperties.PreLoadFilePath");
        item2.addProperty("kind", "string");
        item2.add("ext", item1Ext);

        JsonObject item3 = new JsonObject();

        item3.addProperty("path", "ResourceID");
        item3.addProperty("kind", "string");
        item3.add("ext", item1Ext);

        JsonArray schemaItems = new JsonArray();
        schemaItems.add(item1);
        schemaItems.add(item2);
        schemaItems.add(item3);

        JsonObject schema = new JsonObject();
        schema.addProperty("kind", kind);
        schema.add("schema", schemaItems);

        return schema.toString();
    }

    public static void assertEqualsResponse(ClientResponse response, int expectedProcessed, int expectedUnprocessed) {
        assertEquals(200, response.getStatus());
        assertTrue(response.getType().toString().contains("application/json"));

        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        JsonObject processedJson = json.get("processed").getAsJsonObject();
        JsonArray unprocessedJson = json.get("unprocessed").getAsJsonArray();

        assertEquals(expectedProcessed, processedJson.size());
        assertEquals(expectedUnprocessed, unprocessedJson.size());
    }

    public static boolean IndexedDocumentsExist(ClientResponse response, Integer expectedProcessed) {
        assertEquals(200, response.getStatus());
        assertTrue(response.getType().toString().contains("application/json"));

        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        JsonObject processedJson = json.get("processed").getAsJsonObject();
        return processedJson.size() == expectedProcessed;
    }

    public static void assertNotAuthorized(ClientResponse response) {
        assertEquals(HttpStatus.SC_FORBIDDEN, response.getStatus());
        JsonObject json = new JsonParser().parse(response.getEntity(String.class)).getAsJsonObject();
        assertEquals(403, json.get("code").getAsInt());
        assertEquals("Access denied", json.get("reason").getAsString());
        assertEquals("The user is not authorized to perform this action", json.get("message").getAsString());
    }

    public static void deleteTestBlobs() {
        DeliveryTestUtils.deleteContainer(generateContainerPath(storageAccount, containerName));
        for (String otherContainerName : CONTAINER_NAMES) {
            DeliveryTestUtils.deleteContainer(generateContainerPath(storageAccount, otherContainerName));
        }
    }

    private static String getStorageAccount() {
        return System.getProperty("AZURE_STORAGE_ACCOUNT", System.getenv("AZURE_STORAGE_ACCOUNT"));
    }

    private static String generateContainerPath(String accountName, String containerName) {
        return String.format("https://%s.blob.core.windows.net/%s", accountName, containerName);
    }

    private static String generateBlobPath(String accountName, String containerName, String blobName) {
        return String.format("https://%s.blob.core.windows.net/%s/%s", accountName, containerName, blobName);
    }

    private static String calcBlobAccountUrl(String accountName) {
        return String.format("https://%s.blob.core.windows.net", accountName);
    }

    private static void deleteContainer(String blobUrl) {
        BlobUrlParts parts = BlobUrlParts.parse(blobUrl);

        BlobContainerClient blobContainerClient = getBlobContainerClient(parts.getAccountName(), parts.getBlobContainerName());

        if(blobContainerClient.exists()){
            blobContainerClient.delete();
        }
    }

    private static void createBlob(String blobUrl) {
        BlobUrlParts parts = BlobUrlParts.parse(blobUrl);

        BlobContainerClient blobContainerClient = getBlobContainerClient(parts.getAccountName(), parts.getBlobContainerName());

        if(!blobContainerClient.exists()){
            blobContainerClient.create();
        }

        BlockBlobClient blockBlobClient = blobContainerClient.getBlobClient(parts.getBlobName()).getBlockBlobClient();
        if(!blockBlobClient.exists()) {
            String dataSample = "Sample file for Delivery Integration Test";
            try (ByteArrayInputStream dataStream = new ByteArrayInputStream(dataSample.getBytes())) {
                blockBlobClient.upload(dataStream, dataSample.length());
            } catch (Exception e) {
                e.printStackTrace();
                throw new AssertionError(String.format("Error: Could not create test %s file blob", parts.getBlobName()), e);
            }
        }
    }

    private static void createContainer(String containerUrl) {
        BlobUrlParts parts = BlobUrlParts.parse(containerUrl);

        BlobContainerClient blobContainerClient = getBlobContainerClient(parts.getAccountName(), parts.getBlobContainerName());

        if(!blobContainerClient.exists()){
            blobContainerClient.create();
        }

    }
    private static BlobContainerClient getBlobContainerClient(String accountName, String containerName) {
        String clientSecret = System.getProperty("TESTER_SERVICEPRINCIPAL_SECRET", System.getenv("TESTER_SERVICEPRINCIPAL_SECRET"));
        String clientId = System.getProperty("INTEGRATION_TESTER", System.getenv("INTEGRATION_TESTER"));
        String tenantId = System.getProperty("AZURE_AD_TENANT_ID", System.getenv("AZURE_AD_TENANT_ID"));

        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientSecret(clientSecret)
                .clientId(clientId)
                .tenantId(tenantId)
                .build();

        BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
                .endpoint(calcBlobAccountUrl(accountName))
                .credential(clientSecretCredential)
                .containerName(containerName)
                .buildClient();

        return blobContainerClient;
    }
}
