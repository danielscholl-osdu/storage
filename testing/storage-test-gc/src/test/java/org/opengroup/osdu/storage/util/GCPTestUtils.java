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

import com.google.common.base.Strings;
import java.util.Optional;

public class GCPTestUtils extends TestUtils{

	public GCPTestUtils() {
		domain = Optional.ofNullable(System.getProperty("GROUP_ID", System.getenv("GROUP_ID")))
				.orElse("group");
	}

	@Override
	public synchronized String getToken() throws Exception {
		if (Strings.isNullOrEmpty(token)) {
			String serviceAccountFile = System.getProperty("INTEGRATION_TESTER", System.getenv("INTEGRATION_TESTER"));
			token = new GoogleServiceAccount(serviceAccountFile).getAuthToken();
		}
		return "Bearer " + token;
	}

	@Override
	public synchronized String getNoDataAccessToken() throws Exception {
		if (Strings.isNullOrEmpty(noDataAccesstoken)) {
			String serviceAccountFile = System.getProperty("NO_DATA_ACCESS_TESTER",
					System.getenv("NO_DATA_ACCESS_TESTER"));
			noDataAccesstoken = new GoogleServiceAccount(serviceAccountFile).getAuthToken();
		}
		return "Bearer " + noDataAccesstoken;
	}

	@Override
	public String getDataRootUserToken() throws Exception {
		if (Strings.isNullOrEmpty(dataRootToken)) {
			String serviceAccountFile = System.getProperty("DATA_ROOT_TESTER",
					System.getenv("DATA_ROOT_TESTER"));
			dataRootToken = new GoogleServiceAccount(serviceAccountFile).getAuthToken();
		}
		return "Bearer " + dataRootToken;
	}
}
