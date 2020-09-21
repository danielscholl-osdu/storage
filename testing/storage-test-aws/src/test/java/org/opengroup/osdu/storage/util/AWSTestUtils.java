// Copyright © 2020 Amazon Web Services
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
import org.opengroup.osdu.core.aws.cognito.AWSCognitoClient;

public class AWSTestUtils extends TestUtils {
	private static String token;
	private static String noDataAccesstoken;
	private static AWSCognitoClient awsCognitoClient = null;

	@Override
	public synchronized String getToken() throws Exception {
		if (Strings.isNullOrEmpty(token)) {
			token = getAwsCognitoClient().getTokenForUserWithAccess();
		}
		return "Bearer " + token;
	}

	@Override
	public synchronized String getNoDataAccessToken() throws Exception {
		if (Strings.isNullOrEmpty(noDataAccesstoken)) {
			noDataAccesstoken = getAwsCognitoClient().getTokenForUserWithNoAccess();
		}
		return "Bearer " + noDataAccesstoken;
	}

	private AWSCognitoClient getAwsCognitoClient() {
		if(awsCognitoClient == null)
			awsCognitoClient = new AWSCognitoClient();
		return	awsCognitoClient;
	}
}
