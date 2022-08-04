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

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;

public class AWSCognitoBuilder {
    public static AWSCognitoIdentityProvider generateCognitoClient(){
        AWSCognitoIdentityProviderClientBuilder builder =  AWSCognitoIdentityProviderClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider());
        String region = System.getenv("AWS_COGNITO_REGION");
        if (region != null)
            builder.withRegion(region);
        return builder.build();
    }
}
