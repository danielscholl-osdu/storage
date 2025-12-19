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
		String bearerToken = System.getProperty("INTEGRATION_TESTER_ACCESS_TOKEN", System.getenv("INTEGRATION_TESTER_ACCESS_TOKEN"));
		if(!Strings.isNullOrEmpty(bearerToken) && Strings.isNullOrEmpty(token)) {
			System.out.println("Using INTEGRATION_TESTER_ACCESS_TOKEN bearer token from environment variable");
			token = bearerToken;
		}
		else if (Strings.isNullOrEmpty(token)) {       
			System.out.println("Generating bearer token using SPN client id and secret");      
			String sp_id = System.getProperty("INTEGRATION_TESTER", System.getenv("INTEGRATION_TESTER"));
			String sp_secret = System.getProperty("TESTER_SERVICEPRINCIPAL_SECRET", System.getenv("TESTER_SERVICEPRINCIPAL_SECRET"));
			String tenant_id = System.getProperty("AZURE_AD_TENANT_ID", System.getenv("AZURE_AD_TENANT_ID"));
			String app_resource_id = System.getProperty("AZURE_AD_APP_RESOURCE_ID", System.getenv("AZURE_AD_APP_RESOURCE_ID"));
			token = AzureServicePrincipal.getIdToken(sp_id, sp_secret, tenant_id, app_resource_id);
		}
		return "Bearer " + token;
	}

	@Override
	public synchronized String getNoDataAccessToken() throws Exception {
		String bearerToken = System.getProperty("NO_DATA_ACCESS_TESTER_ACCESS_TOKEN", System.getenv("NO_DATA_ACCESS_TESTER_ACCESS_TOKEN"));
		if(!Strings.isNullOrEmpty(bearerToken) && Strings.isNullOrEmpty(noDataAccesstoken)) {
			System.out.println("Using bearer NO_DATA_ACCESS_TESTER_ACCESS_TOKEN token from environment variable");
			noDataAccesstoken = bearerToken;
		}
		else if (Strings.isNullOrEmpty(noDataAccesstoken)) {       
			System.out.println("Generating bearer token using SPN client id and secret");      
			String sp_id = System.getProperty("NO_DATA_ACCESS_TESTER", System.getenv("NO_DATA_ACCESS_TESTER"));
			String sp_secret = System.getProperty("NO_DATA_ACCESS_TESTER_SERVICEPRINCIPAL_SECRET", System.getenv("NO_DATA_ACCESS_TESTER_SERVICEPRINCIPAL_SECRET"));
			String tenant_id = System.getProperty("AZURE_AD_TENANT_ID", System.getenv("AZURE_AD_TENANT_ID"));
			String app_resource_id = System.getProperty("AZURE_AD_APP_RESOURCE_ID", System.getenv("AZURE_AD_APP_RESOURCE_ID"));
			noDataAccesstoken = AzureServicePrincipal.getIdToken(sp_id, sp_secret, tenant_id, app_resource_id);
		}
		return "Bearer " + noDataAccesstoken;
	}

}
