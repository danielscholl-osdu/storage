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

package org.opengroup.osdu.storage.provider.aws.util;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.opengroup.osdu.core.aws.entitlements.ServicePrincipal;
import org.opengroup.osdu.core.aws.iam.IAMConfig;
import org.opengroup.osdu.core.aws.secrets.SecretsManager;
import com.amazonaws.auth.AWSCredentialsProvider;
import javax.annotation.PostConstruct;



@Component
public class ServiceAccountJwtClientImpl implements IServiceAccountJwtClient {

    @Value("${aws.region}")
    @Getter()
    @Setter(AccessLevel.PROTECTED)
    public String amazonRegion;



    @Value("${aws.ssm}")
    @Getter()
    @Setter(AccessLevel.PROTECTED)
    public Boolean ssmEnabled;


    @Value("${aws.environment}")
    @Getter()
    @Setter(AccessLevel.PROTECTED)
    public String environment;

    private String awsOauthCustomScope;

    String client_credentials_secret;
    String client_credentials_clientid;
    ServicePrincipal sp;


    private AWSCredentialsProvider amazonAWSCredentials;
    private AWSSimpleSystemsManagement ssmManager;


    @PostConstruct
    public void init() {
        if (ssmEnabled) {

            SecretsManager sm = new SecretsManager();

            String oauth_token_url = "/osdu/" + environment + "/oauth-token-uri";
            String oauth_custom_scope = "/osdu/" + environment + "/oauth-custom-scope";

            String client_credentials_client_id = "/osdu/" + environment + "/client-credentials-client-id";
            String client_secret_key = "client_credentials_client_secret";
            String client_secret_secretName = "/osdu/" + environment + "/client_credentials_secret";

            amazonAWSCredentials = IAMConfig.amazonAWSCredentials();
            ssmManager = AWSSimpleSystemsManagementClientBuilder.standard()
                    .withCredentials(amazonAWSCredentials)
                    .withRegion(amazonRegion)
                    .build();

            client_credentials_clientid = getSsmParameter(client_credentials_client_id);

            client_credentials_secret = sm.getSecret(client_secret_secretName,amazonRegion,client_secret_key);

            String tokenUrl = getSsmParameter(oauth_token_url);

            awsOauthCustomScope = getSsmParameter(oauth_custom_scope);

            sp = new ServicePrincipal(amazonRegion,environment,tokenUrl,awsOauthCustomScope);

        }
    }

    @Override
    public String getIdToken(String tenantName)
    {
        String token=  sp.getServicePrincipalAccessToken(client_credentials_clientid,client_credentials_secret);
        return token;


    }


    private String getSsmParameter(String parameterKey) {
        GetParameterRequest paramRequest = (new GetParameterRequest()).withName(parameterKey).withWithDecryption(true);
        GetParameterResult paramResult = ssmManager.getParameter(paramRequest);
        return paramResult.getParameter().getValue();
    }


}
