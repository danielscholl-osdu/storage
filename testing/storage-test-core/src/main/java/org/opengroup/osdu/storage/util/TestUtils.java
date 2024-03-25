// Copyright 2017-2019, Schlumberger
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

import com.google.gson.Gson;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.http.HttpStatus.SC_CREATED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opengroup.osdu.storage.util.HeaderUtils.getHeadersWithxCollaboration;
import static org.opengroup.osdu.storage.util.TestBase.GSON;

public abstract class TestUtils {
    protected static String token = null;
    protected static String noDataAccesstoken = null;
    protected static String dataRootToken = null;
    private static Gson gson = new Gson();

    protected static String domain = System.getProperty("DOMAIN", System.getenv("DOMAIN"));

    public static final String getDomain() {
        return domain;
    }

    public static final String getAclSuffix() {
        String environment = getEnvironment();
        //build.gradle currently throws exception if a variable is set to empty or not set at all
        //workaround by setting it to an "empty" string to construct the url
        if (environment.equalsIgnoreCase("empty")) environment = "";
        if (!environment.isEmpty())
            environment = "." + environment;

        return String.format("%s%s.%s", TenantUtils.getTenantName(), environment, domain);
    }

    public static final String getAcl() {
        return String.format("data.test1@%s", getAclSuffix());
    }

    public static final String getEntV2OnlyAcl() {
        return String.format("data.storage-integration-test-acl.ent-v2@%s", getAclSuffix());
    }

    public static final String getIntegrationTesterAcl() {
        return String.format("data.integration.test@%s", getAclSuffix());
    }

    public static final String getPubsubToken() {
        return System.getProperty("PUBSUB_TOKEN", System.getenv("PUBSUB_TOKEN"));
    }

    public static String getEnvironment() {
        return System.getProperty("DEPLOY_ENV", System.getenv("DEPLOY_ENV"));
    }

    public static String getApiPath(String api) throws MalformedURLException {
        String baseUrl = System.getProperty("STORAGE_URL", System.getenv("STORAGE_URL"));
        if (baseUrl == null || baseUrl.contains("-null")) {
            baseUrl = "https://localhost:8443/api/storage/v2/";
        }
        URL mergedURL = new URL(baseUrl + api);
        System.out.println(mergedURL.toString());
        return mergedURL.toString();
    }

    public static void assertRecordVersion(CloseableHttpResponse response, Long expectedVersion) {
        assertEquals(HttpStatus.SC_OK, response.getCode());

        String responseBody;
        try {
            responseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        DummyRecordsHelper.RecordResultMock result = gson.fromJson(responseBody, DummyRecordsHelper.RecordResultMock.class);
        assertEquals(expectedVersion.longValue(), Long.parseLong(result.version));
    }

    public static String assertRecordVersionAndReturnResponseBody(CloseableHttpResponse response, Long expectedVersion) {
        assertEquals(HttpStatus.SC_OK, response.getCode());

        String responseBody;
        try {
            responseBody = EntityUtils.toString(response.getEntity());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        DummyRecordsHelper.RecordResultMock result = gson.fromJson(responseBody, DummyRecordsHelper.RecordResultMock.class);
        assertEquals(expectedVersion.longValue(), Long.parseLong(result.version));
        return responseBody;
    }

    public abstract String getToken() throws Exception;

    public abstract String getNoDataAccessToken() throws Exception;

    public String getDataRootUserToken() throws Exception{
        throw new NotImplementedException();
    }

    public static CloseableHttpResponse sendWithCustomMediaType(String path, String httpMethod, Map<String, String> headers, String contentType, String requestBody,
                                                                String query) throws Exception {

        log(httpMethod, TestUtils.getApiPath(path + query), headers, requestBody);
        BasicHttpClientConnectionManager cm = createBasicHttpClientConnectionManager();
        headers.put("Content-Type", contentType);
        ClassicHttpRequest httpRequest = createHttpRequest(TestUtils.getApiPath(path + query), httpMethod, requestBody, headers);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(cm).build()) {
            return httpClient.execute(httpRequest, new CustomHttpClientResponseHandler());
        }
    }

    public static CloseableHttpResponse send(String path, String httpMethod, Map<String, String> headers, String requestBody,
                                             String query) throws Exception {

        log(httpMethod, TestUtils.getApiPath(path + query), headers, requestBody);

        BasicHttpClientConnectionManager cm = createBasicHttpClientConnectionManager();
        headers.put("Content-Type", MediaType.APPLICATION_JSON);
        headers.put("Accept-Charset","utf-8");
        ClassicHttpRequest httpRequest = createHttpRequest(TestUtils.getApiPath(path + query), httpMethod, requestBody, headers);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(cm).build()) {
            return httpClient.execute(httpRequest, new CustomHttpClientResponseHandler());
        }
    }

    public static CloseableHttpResponse send(String url, String path, String httpMethod, Map<String, String> headers,
                                             String requestBody, String query) throws Exception {

        log(httpMethod, url + path, headers, requestBody);

        BasicHttpClientConnectionManager cm = createBasicHttpClientConnectionManager();
        headers.put("Content-Type", MediaType.APPLICATION_JSON);
        ClassicHttpRequest httpRequest = createHttpRequest(url + path, httpMethod, requestBody, headers);

        try (CloseableHttpClient httpClient = HttpClientBuilder.create().setConnectionManager(cm).build()) {
            return httpClient.execute(httpRequest, new CustomHttpClientResponseHandler());
        }
    }

    private static void log(String method, String url, Map<String, String> headers, String body) {
        System.out.println(String.format("%s: %s", method, url));
        System.out.println(body);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getResult(CloseableHttpResponse response, int exepectedStatus, Class<T> classOfT) {
        assertEquals(exepectedStatus, response.getCode());
        if (exepectedStatus == 204) {
            return null;
        }

        assertTrue(response.getEntity().getContentType().contains("application/json"));
        String json;
        try {
            json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        if (classOfT == String.class) {
            return (T) json;
        }
        return gson.fromJson(json, classOfT);
    }

    public static Long createRecordInCollaborationContext_AndReturnVersion(String recordId, String kind, String legaltag, String collaborationId, String applicationName, String tenant_name, String token) throws Exception {
        String jsonInput = RecordUtil.createDefaultJsonRecord(recordId, kind, legaltag);

        CloseableHttpResponse response = TestUtils.send("records", "PUT", getHeadersWithxCollaboration(collaborationId, applicationName, tenant_name, token), jsonInput, "");
        assertEquals(SC_CREATED, response.getCode());
        assertTrue(response.getEntity().getContentType().contains("application/json"));

        String responseBody = EntityUtils.toString(response.getEntity());
        DummyRecordsHelper.CreateRecordResponse result = GSON.fromJson(responseBody, DummyRecordsHelper.CreateRecordResponse.class);

        return Long.parseLong(result.recordIdVersions[0].split(":")[3]);
    }

    private static ClassicHttpRequest createHttpRequest(String path, String httpMethod, String requestBody,
                                                        Map<String, String> headers) throws MalformedURLException {
        ClassicRequestBuilder classicRequestBuilder = ClassicRequestBuilder.create(httpMethod)
                .setUri(path)
                .setEntity(requestBody, ContentType.APPLICATION_JSON);
        headers.forEach(classicRequestBuilder::addHeader);
        return classicRequestBuilder.build();
    }


    private static BasicHttpClientConnectionManager createBasicHttpClientConnectionManager() {
    ConnectionConfig connConfig = ConnectionConfig.custom()
            .setConnectTimeout(1500000, TimeUnit.MILLISECONDS)
            .setSocketTimeout(1500000, TimeUnit.MILLISECONDS)
            .build();
    BasicHttpClientConnectionManager cm = new BasicHttpClientConnectionManager();
    cm.setConnectionConfig(connConfig);
    return cm;
  }

}
