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

package org.opengroup.osdu.storage.query;

import java.util.List;
import org.opengroup.osdu.core.test.auth.UserType;
import org.opengroup.osdu.core.test.base.BaseGetInfoAcceptanceTests;
import org.opengroup.osdu.core.test.service.ServiceType;

public final class GetQueryInfoIntegrationTest extends BaseGetInfoAcceptanceTests {

  public GetQueryInfoIntegrationTest() {
    super(UserType.PRIVILEGED_USER, ServiceType.STORAGE_V2,
        List.of("collaborations-enabled", "featureFlag.opa.enabled"));
  }
}
