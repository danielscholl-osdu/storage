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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.model.ReplayStatusResponseHelper;

import java.io.IOException;
import java.util.List;
public class ReplayUtils {

    public static String createJsonEmpty() {

        JsonObject requestBody = new JsonObject();
        return requestBody.toString();
    }

    public static String createJsonWithoutOperationName(List<String> kindList) {

        JsonObject requestBody = new JsonObject();
        JsonObject filter = new JsonObject();
        requestBody.add("filter", filter);
        JsonArray kindArray = new JsonArray();
        filter.add("kinds", kindArray);
        for (String kind : kindList)
            kindArray.add(kind);
        return requestBody.toString();
    }


    public static String createJsonWithOperationName(String operation) {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("operation", operation);
        return requestBody.toString();
    }

    public static String createJsonWithEmptyFilter(String operation) {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("operation", operation);
        JsonObject filter = new JsonObject();
        requestBody.add("filter", filter);
        return requestBody.toString();
    }

    public static String createJsonWithKind(String operation, List<String> kindList) {

        JsonObject requestBody = new JsonObject();
        JsonObject filter = new JsonObject();
        JsonArray kindArray = new JsonArray();
        requestBody.addProperty("operation", operation);
        requestBody.add("filter", filter);
        filter.add("kinds", kindArray);
        for (String kind : kindList)
            kindArray.add(kind);
        return requestBody.toString();
    }

    public static String getFieldFromResponse(CloseableHttpResponse response, String field) throws IOException, ParseException {

        return JsonParser.parseString(EntityUtils.toString(response.getEntity()))
                         .getAsJsonObject()
                         .get(field)
                         .getAsString();
    }

    public static String getSearchCountQueryForKind(String kind) {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("kind", kind);
        requestBody.addProperty("limit", 1);
        requestBody.addProperty("trackTotalCount", true);
        return requestBody.toString();
    }

    public static String getSearchUrl() {

        String searchUrl = System.getProperty("SEARCH_URL", System.getenv("SEARCH_URL"));
        if (searchUrl == null || searchUrl.contains("-null")) {
            throw new IllegalArgumentException("Invalid SEARCH_URL: " + searchUrl);
        }
        return searchUrl;
    }

    public static String getIndexerUrl() {

        String indexerUrl = System.getProperty("INDEXER_URL", System.getenv("INDEXER_URL"));
        if (indexerUrl == null || indexerUrl.contains("-null")) {
            throw new IllegalArgumentException("Invalid INDEXER_URL: " + indexerUrl);
        }
        return indexerUrl;
    }

    public static ReplayStatusResponseHelper getConvertedReplayStatusResponseFromResponse(CloseableHttpResponse response) throws ProtocolException, IOException {

        String json = EntityUtils.toString(response.getEntity());
        Gson gson = new Gson();
        return gson.fromJson(json, ReplayStatusResponseHelper.class);
    }
}
