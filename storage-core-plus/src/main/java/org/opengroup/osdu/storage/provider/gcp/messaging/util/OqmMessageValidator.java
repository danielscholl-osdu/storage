/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.messaging.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;

@Slf4j
@UtilityClass
public final class OqmMessageValidator {

  private static final String BODY = "body";
  private static final ObjectMapper mapper = new ObjectMapper();

  public static boolean isValid(OqmMessage oqmMessage) {

    if (Objects.isNull(oqmMessage)) {
      log.error("OqmMessage is null");
      return false;
    }

    if (Strings.isNullOrEmpty(oqmMessage.getData()) || "{}".equals(oqmMessage.getData())) {
      log.error(
          "Message body is empty, message id: {}, attributes: {}",
          oqmMessage.getId(),
          oqmMessage.getAttributes());
      return false;
    }

    if (!isJsonArrayWithBody(oqmMessage.getData())) {
      log.error("Invalid JSON structure in message id: {}", oqmMessage.getId());
      return false;
    }

    try {
      if (Objects.isNull(oqmMessage.getAttributes()) || oqmMessage.getAttributes().isEmpty()) {
        log.error(
            "Attribute map not found, message id: {}, attributes: {}",
            oqmMessage.getId(),
            oqmMessage.getAttributes());
        return false;
      }
    } catch (NullPointerException e) {
      log.error("Attribute map not found, message id: {}", oqmMessage.getId());
      return false;
    }

    // headerAttributes
    // "account-id" -> <Tenant_Id>
    // "data-partition-id" -> <Tenant_Id>
    // "user" -> <User_Name>
    // "correlation-id" -> <GUID>
    if (Objects.isNull(oqmMessage.getAttributes().get(DpsHeaders.DATA_PARTITION_ID))) {
      log.error(
          "Missing '{}' attribute. Message ID: {}, attributes: {}",
          DpsHeaders.DATA_PARTITION_ID,
          oqmMessage.getId(),
          oqmMessage.getAttributes());
      return false;
    }
    return true;
  }

  private static boolean isJsonArrayWithBody(String json) {
    try {
      JsonNode root = mapper.readTree(json);
      if (!root.isArray()) return false;

      for (JsonNode item : root) {
        if (!item.hasNonNull(BODY)) {
          log.error("Body is null or missing in one of the items.");
          return false;
        }
      }
      return true;
    } catch (Exception e) {
      log.error("Failed to parse JSON: {}", e.getMessage(), e);
      return false;
    }
  }
}
