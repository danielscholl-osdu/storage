/*
 *  Copyright @ Microsoft Corporation
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;

@ExtendWith(MockitoExtension.class)
class OqmMessageValidatorTest {

  private static final String EMPTY_DATA = "{}";
  private static final String TENANT_ID_1 = "opendes";
  private static final String ACCOUNT_ID_1 = "accountX";
  private static final String TEST_USER_1 = "test-user";
  private static final String CORR_ID_1 = "corr-id-123";

  @Test
  void shouldReturnFalse_whenMessageIsNull() {
    assertFalse(OqmMessageValidator.isValid(null));
  }

  @Test
  void shouldReturnFalse_whenDataIsNull() {

    OqmMessage message = OqmMessage.builder().data(null).attributes(validAttributes()).build();
    assertFalse(OqmMessageValidator.isValid(message));
  }

  @Test
  void shouldReturnFalse_whenDataIsEmptyJson() {
    OqmMessage message =
        OqmMessage.builder().data(EMPTY_DATA).attributes(validAttributes()).build();
    assertFalse(OqmMessageValidator.isValid(message));
  }

  @Test
  void shouldReturnFalse_whenAttributesMapIsNull() {
    OqmMessage message = OqmMessage.builder().data(validData()).attributes(null).build();
    assertFalse(OqmMessageValidator.isValid(message));
  }

  @Test
  void shouldReturnFalse_whenAttributesMapIsEmpty() {
    OqmMessage message = OqmMessage.builder().data(validData()).attributes(new HashMap<>()).build();
    assertFalse(OqmMessageValidator.isValid(message));
  }

  @Test
  void shouldReturnFalse_whenDataPartitionIdIsMissing() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(DpsHeaders.USER_EMAIL, TEST_USER_1);
    attributes.put(DpsHeaders.CORRELATION_ID, CORR_ID_1);

    OqmMessage message = OqmMessage.builder().data(validData()).attributes(attributes).build();

    assertFalse(OqmMessageValidator.isValid(message));
  }

  @Test
  void shouldReturnTrue_whenMessageIsValid() {
    OqmMessage message =
        OqmMessage.builder().data(validData()).attributes(validAttributes()).build();
    assertTrue(OqmMessageValidator.isValid(message));
  }

  private Map<String, String> validAttributes() {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(DpsHeaders.DATA_PARTITION_ID, TENANT_ID_1);
    attributes.put(DpsHeaders.ACCOUNT_ID, ACCOUNT_ID_1);
    attributes.put(DpsHeaders.USER_EMAIL, TEST_USER_1);
    attributes.put(DpsHeaders.CORRELATION_ID, CORR_ID_1);
    return attributes;
  }

  private static String validData() {
    return "[ {\n"
        + "  \"headers\" : {\n"
        + "    \"correlation-id\" : \""
        + CORR_ID_1
        + "\",\n"
        + "    \"data-partition-id\" : \""
        + TENANT_ID_1
        + "\"\n"
        + "  },\n"
        + "  \"body\" : {\n"
        + "    \"id\" : \"d76f2505-354d-44be-b8b0-9ee3057bbacc\",\n"
        + "    \"operation\" : \"replay\",\n"
        + "    \"replayType\" : \"REPLAY_KIND\",\n"
        + "    \"replayId\" : \"256500cc-d9e0-462b-8471-011b5e42f39b\",\n"
        + "    \"kind\" : \"kind-1\",\n"
        + "    \"completionCount\" : 0,\n"
        + "    \"totalCount\" : 42920,\n"
        + "    \"startAtTimestamp\" : 1746440484490\n"
        + "  }\n"
        + "} ]";
  }
}
