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

import com.google.api.client.util.Strings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeaderUtils {

  protected static final String COLLABORATION_HEADER = "x-collaboration";

  public static Map<String, String> getHeaders(String tenantName, String token) {
    Map<String, String> headers = new HashMap<>();
    if (tenantName == null || tenantName.isEmpty()) {
      tenantName = TenantUtils.getTenantName();
    }
    headers.put("Data-Partition-Id", tenantName);
    headers.put("Authorization", token);

    final String correlationId = UUID.randomUUID().toString();
    System.out.printf("Using correlation-id for the request: %s \n", correlationId);
    headers.put("correlation-id", correlationId);

    return headers;
  }

  public static Map<String, String> getHeadersWithxCollaboration(String collaborationId,
      String applicationName, String tenantName, String token) {
    Map<String, String> headers = getHeaders(tenantName, token);
    if (!Strings.isNullOrEmpty(collaborationId)) {
      headers.put(COLLABORATION_HEADER,
          "id=" + collaborationId + ",application=" + applicationName);
    }
    return headers;
  }

  public static Map<String, String> getHeadersWithxCollaborationWithoutId(String collaborationId,
      String applicationName, String tenantName, String token) {
    Map<String, String> headers = getHeaders(tenantName, token);
    if (Strings.isNullOrEmpty(collaborationId)) {
      headers.put(COLLABORATION_HEADER, "application=" + applicationName);
    }else {
      headers.put(COLLABORATION_HEADER, "id=" + collaborationId + ",application=" + applicationName);
    }
    return headers;
  }

  public static Map<String, String> getHeadersWithoutAuth(String tenantName, String token) {
    Map<String, String> headers = new HashMap<>();
    if (tenantName == null || tenantName.isEmpty()) {
      tenantName = TenantUtils.getTenantName();
    }
    headers.put("Data-Partition-Id", tenantName);

    final String correlationId = UUID.randomUUID().toString();
    System.out.printf("Using correlation-id for the request: %s \n", correlationId);
    headers.put("correlation-id", correlationId);

    return headers;
  }

  public static Map<String, String> getHeadersWithoutDataPartitionId(String tenantName,
      String token) {
    Map<String, String> headers = new HashMap<>();
    if (tenantName == null || tenantName.isEmpty()) {
      tenantName = TenantUtils.getTenantName();
    }
    headers.put("Authorization", token);

    final String correlationId = UUID.randomUUID().toString();
    System.out.printf("Using correlation-id for the request: %s \n", correlationId);
    headers.put("correlation-id", correlationId);

    return headers;
  }

}
