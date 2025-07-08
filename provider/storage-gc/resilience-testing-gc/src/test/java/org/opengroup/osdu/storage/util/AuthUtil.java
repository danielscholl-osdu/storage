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

package org.opengroup.osdu.storage.util;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import java.io.IOException;

public class AuthUtil {

  private static String idToken;

  public static String getToken() {
    try {
      if (idToken == null) {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        IdTokenCredentials idTokenCredentials = IdTokenCredentials.newBuilder()
            .setIdTokenProvider((IdTokenProvider) credentials)
            .setTargetAudience("osdu")
            .build();
        idTokenCredentials.refresh();
        idToken = "Bearer " + idTokenCredentials.getIdToken().getTokenValue();
      }
      return idToken;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
