/*
 *  Copyright 2020-2023 Google LLC
 *  Copyright 2020-2023 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import java.util.Map;
import org.springframework.http.HttpMethod;

public class EntitlementsUtil {

  public static final String GROUPS_ENDPOINT = "groups";

  public static ClientResponse createEntitlementsGroup(Map<String, String> headers,
      String groupName,
      String groupDescription) throws Exception {
    String body = getCreateGroupBody(groupName, groupDescription);
    ClientResponse response = TestUtils.send(
        getEntitlementsUrl(),
        GROUPS_ENDPOINT,
        HttpMethod.POST.name(),
        headers,
        body,
        ""
    );
    return response;
  }

  public static ClientResponse deleteEntitlementsGroup(Map<String, String> headers,
      String groupEmail)
      throws Exception {
    ClientResponse response = TestUtils.send(
        getEntitlementsUrl(),
        GROUPS_ENDPOINT,
        HttpMethod.DELETE.name(),
        headers,
        "",
        groupEmail
    );
    return response;
  }

  protected static String getCreateGroupBody(String name, String groupDescription) {
    JsonObject groupBody = new JsonObject();
    groupBody.addProperty("name", name);
    groupBody.addProperty("description", groupDescription);
    return groupBody.toString();
  }

  protected static String getEntitlementsUrl() {
    return System.getProperty("ENTITLEMENTS_URL", System.getenv("ENTITLEMENTS_URL"));
  }
}
