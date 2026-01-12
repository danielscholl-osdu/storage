// Copyright © 2020 Amazon Web Services
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

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthResult;

import java.util.HashMap;
import java.util.Map;

public class AWSCognitoClient {
    public static String getTokenForUserWithAccess(){
        String clientId = System.getProperty("AWS_COGNITO_CLIENT_ID", System.getenv("AWS_COGNITO_CLIENT_ID"));
        String authFlow = System.getProperty("AWS_COGNITO_AUTH_FLOW", System.getenv("AWS_COGNITO_AUTH_FLOW"));
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", System.getProperty("AWS_COGNITO_AUTH_PARAMS_USER", System.getenv("AWS_COGNITO_AUTH_PARAMS_USER")));
        authParameters.put("PASSWORD", System.getProperty("AWS_COGNITO_AUTH_PARAMS_PASSWORD", System.getenv("AWS_COGNITO_AUTH_PARAMS_PASSWORD")));

        AWSCognitoIdentityProvider provider = AWSCognitoBuilder.generateCognitoClient();
        InitiateAuthRequest request = new InitiateAuthRequest();
        request.setClientId(clientId);
        request.setAuthFlow(authFlow);
        request.setAuthParameters(authParameters);

        InitiateAuthResult result = provider.initiateAuth(request);
        return result.getAuthenticationResult().getAccessToken();
    }

    public static String getTokenForUserWithNoAccess(){
        String clientId = System.getProperty("AWS_COGNITO_CLIENT_ID", System.getenv("AWS_COGNITO_CLIENT_ID"));
        String authFlow = System.getProperty("AWS_COGNITO_AUTH_FLOW", System.getenv("AWS_COGNITO_AUTH_FLOW"));
        Map<String, String> authParameters = new HashMap<>();
        authParameters.put("USERNAME", System.getProperty("AWS_COGNITO_AUTH_PARAMS_USER_NO_ACCESS", System.getenv("AWS_COGNITO_AUTH_PARAMS_USER_NO_ACCESS")));
        authParameters.put("PASSWORD", System.getProperty("AWS_COGNITO_AUTH_PARAMS_PASSWORD", System.getenv("AWS_COGNITO_AUTH_PARAMS_PASSWORD")));

        AWSCognitoIdentityProvider provider = AWSCognitoBuilder.generateCognitoClient();
        InitiateAuthRequest request = new InitiateAuthRequest();
        request.setClientId(clientId);
        request.setAuthFlow(authFlow);
        request.setAuthParameters(authParameters);

        InitiateAuthResult result = provider.initiateAuth(request);
        return result.getAuthenticationResult().getAccessToken();
    }
}
