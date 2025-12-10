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

public class TokenTestUtils extends TestUtils {

  public static final String INTEGRATION_TESTER_TOKEN = "PRIVILEGED_USER_TOKEN";
  public static final String NO_DATA_ACCESS_TOKEN = "NO_ACCESS_USER_TOKEN";
  public static final String DATA_ROOT_TOKEN = "ROOT_USER_TOKEN";
  private OpenIDTokenProvider openIDTokenProvider;

  public TokenTestUtils() {
    token = System.getProperty(INTEGRATION_TESTER_TOKEN, System.getenv(INTEGRATION_TESTER_TOKEN));
    noDataAccesstoken = System.getProperty(NO_DATA_ACCESS_TOKEN, System.getenv(NO_DATA_ACCESS_TOKEN));
    dataRootToken = System.getProperty(DATA_ROOT_TOKEN, System.getenv(DATA_ROOT_TOKEN));

    if (Strings.isNullOrEmpty(token) || Strings.isNullOrEmpty(noDataAccesstoken) || Strings.isNullOrEmpty(dataRootToken)) {
      openIDTokenProvider = new OpenIDTokenProvider();
    }
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
