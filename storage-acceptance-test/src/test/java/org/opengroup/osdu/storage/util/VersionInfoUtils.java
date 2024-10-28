/*
 *  Copyright 2020-2024 Google LLC
 *  Copyright 2020-2024 EPAM Systems, Inc
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.io.IOException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

public class VersionInfoUtils {

  public VersionInfo getVersionInfoFromResponse(CloseableHttpResponse response) {
    assertTrue(response.getEntity().getContentType().contains("application/json"));
    String json = null;
    try {
      json = EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
    Gson gson = new Gson();
    return gson.fromJson(json, VersionInfo.class);
  }

  public class VersionInfo {
    public String groupId;
    public String artifactId;
    public String version;
    public String buildTime;
    public String branch;
    public String commitId;
    public String commitMessage;
  }
}
