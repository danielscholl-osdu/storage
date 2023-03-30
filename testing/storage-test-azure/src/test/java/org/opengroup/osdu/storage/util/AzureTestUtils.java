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

public class AzureTestUtils extends TestUtils {

	@Override
	public synchronized String getToken() throws Exception {
		if (Strings.isNullOrEmpty(token)) {
			String sp_id = "53f0e474-22a1-4144-8511-af8096809920";
			String sp_secret = "FyX7Q~z2I1KsSjTqpfR0U_bpuOnVF~JJaN16u";
			String tenant_id = "214a5305-e9b5-4cc4-9771-ed13e9b53f8f";
			String app_resource_id = "f78b3f23-a412-4696-b0c3-c198df8c235d";
			token = AzureServicePrincipal.getIdToken(sp_id, sp_secret, tenant_id, app_resource_id);
		}
		return "Bearer " + token;
	}

	@Override
	public synchronized String getNoDataAccessToken() throws Exception {
		if (Strings.isNullOrEmpty(noDataAccesstoken)) {
			String sp_id = "3b456202-fb8b-4ac6-88f6-9aa057a24cda";
			String sp_secret = "oqG7Q~UpBBEDbdY0hXhS-Y-a.tN3S8l3RsvZf";
			String tenant_id = "214a5305-e9b5-4cc4-9771-ed13e9b53f8f";
			String app_resource_id = "f78b3f23-a412-4696-b0c3-c198df8c235d";
			noDataAccesstoken = AzureServicePrincipal.getIdToken(sp_id, sp_secret, tenant_id, app_resource_id);
		}
		return "Bearer " + noDataAccesstoken;
	}

}
