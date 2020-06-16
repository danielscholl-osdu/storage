package org.opengroup.osdu.storage.util;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;

public class AWSCognitoBuilder {
    public static AWSCognitoIdentityProvider generateCognitoClient(){
        return AWSCognitoIdentityProviderClientBuilder.standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .build();
    }
}
