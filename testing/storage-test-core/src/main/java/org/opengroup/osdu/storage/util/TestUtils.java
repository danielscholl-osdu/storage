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

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MediaType;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public abstract class TestUtils {
    protected static String token = null;
    protected static String noDataAccesstoken = null;

    protected static final String domain = System.getProperty("DOMAIN", System.getenv("DOMAIN"));

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

    public static final String getIntegrationTesterAcl() {
        return String.format("data.integration.test@%s", getAclSuffix());
    }

    public static final String getPubsubToken() {
        return System.getProperty("PUBSUB_TOKEN", System.getenv("PUBSUB_TOKEN"));
    }

    public static String getEnvironment() {
        return System.getProperty("DEPLOY_ENV", System.getenv("DEPLOY_ENV"));
    }

    public static String getApiPath(String api) throws Exception {
        String baseUrl = System.getProperty("STORAGE_URL", System.getenv("STORAGE_URL"));
        if (baseUrl == null || baseUrl.contains("-null")) {
            baseUrl = "https://localhost:8443/api/storage/v2/";
        }
        URL mergedURL = new URL(baseUrl + api);
        System.out.println(mergedURL.toString());
        return mergedURL.toString();
    }

    public abstract String getToken() throws Exception;

    public abstract String getNoDataAccessToken() throws Exception;

    public static ClientResponse send(String path, String httpMethod, Map<String, String> headers, String requestBody,
                                      String query) throws Exception {

        log(httpMethod, TestUtils.getApiPath(path + query), headers, requestBody);
        Client client = TestUtils.getClient();

        WebResource webResource = client.resource(TestUtils.getApiPath(path + query));

        WebResource.Builder builder = webResource.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON);
        headers.forEach(builder::header);

        return builder.method(httpMethod, ClientResponse.class, requestBody);
    }

    public static ClientResponse send(String url, String path, String httpMethod, Map<String, String> headers,
                                      String requestBody, String query) throws Exception {

        log(httpMethod, url + path, headers, requestBody);
        Client client = TestUtils.getClient();

        WebResource webResource = client.resource(url + path);
        WebResource.Builder builder = webResource.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON);
        headers.forEach(builder::header);

        return builder.method(httpMethod, ClientResponse.class, requestBody);
    }

    private static void log(String method, String url, Map<String, String> headers, String body) {
        System.out.println(String.format("%s: %s", method, url));
        System.out.println(body);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getResult(ClientResponse response, int exepectedStatus, Class<T> classOfT) {
        assertEquals(exepectedStatus, response.getStatus());
        if (exepectedStatus == 204) {
            return null;
        }

        assertEquals("application/json; charset=UTF-8", response.getType().toString());
        String json = response.getEntity(String.class);
        if (classOfT == String.class) {
            return (T) json;
        }

        Gson gson = new Gson();
        return gson.fromJson(json, classOfT);
    }

    protected static Client getClient() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
        }
        allowMethods("PATCH");
        return Client.create();
    }

    private static void allowMethods(String... methods) {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);

            methodsField.setAccessible(true);

            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.addAll(Arrays.asList(methods));
            String[] newMethods = methodsSet.toArray(new String[0]);

            methodsField.set(null/*static field*/, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
