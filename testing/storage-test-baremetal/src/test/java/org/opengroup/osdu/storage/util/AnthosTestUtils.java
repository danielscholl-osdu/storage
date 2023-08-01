/*
 * Copyright 2020-2023 Google LLC
 * Copyright 2020-2023 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;

import java.util.Optional;

public class AnthosTestUtils extends TestUtils {

  private static final OpenIDTokenProvider openIDTokenProvider = new OpenIDTokenProvider();

  public AnthosTestUtils() {
    domain =
        Optional.ofNullable(System.getProperty("GROUP_ID", System.getenv("GROUP_ID")))
            .orElse("group");
  }

  @Override
  public synchronized String getToken() throws Exception {
    if (Strings.isNullOrEmpty(token)) {
      token = openIDTokenProvider.getToken();
    }
    return "Bearer " + token;
  }

  @Override
  public synchronized String getNoDataAccessToken() throws Exception {
    if (Strings.isNullOrEmpty(noDataAccesstoken)) {
      noDataAccesstoken = openIDTokenProvider.getNoAccessToken();
    }
    return "Bearer " + noDataAccesstoken;
  }

  @Override
  public String getDataRootUserToken() throws Exception {
    if (Strings.isNullOrEmpty(dataRootToken)) {
      dataRootToken = openIDTokenProvider.getDataRootToken();
    }
    return "Bearer " + dataRootToken;
  }
}
