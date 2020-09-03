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

package compliance;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opengroup.osdu.core.aws.cognito.AWSCognitoClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplianceTriggerFunctionHelper {
    private final static String COGNITO_CLIENT_ID_PROPERTY = "AWS_COGNITO_CLIENT_ID";
    private final static String COGNITO_AUTH_FLOW_PROPERTY = "AWS_COGNITO_AUTH_FLOW";
    private final static String COMPLIANCE_CREDENTIALS_SECRET_NAME = "ComplianceTriggerCredentials";
    private final static String SECRETS_REGION = "us-east-1";
    private final static String SECRETS_ENDPOINT = "secretsmanager.us-east-1.amazonaws.com";
    private final static String COMPLIANCE_USER_KEY = "ComplianceTriggerUser";
    private final static String COMPLIANCE_USER_PWD_KEY = "ComplianceTriggerUserPassword";

    public String getAccessToken() {
        String clientId = System.getenv(COGNITO_CLIENT_ID_PROPERTY);
        String authFlow = System.getenv(COGNITO_AUTH_FLOW_PROPERTY);
        Map<String, String> userSecretCredentials = getUserSecretCredentials();

        AWSCognitoClient client = new AWSCognitoClient(clientId, authFlow, userSecretCredentials.get(COMPLIANCE_USER_KEY),
                userSecretCredentials.get(COMPLIANCE_USER_PWD_KEY));

        return "Bearer " + client.getToken();
    }

    public Map<String, String> getUserSecretCredentials() {
        AwsClientBuilder.EndpointConfiguration config = new AwsClientBuilder.EndpointConfiguration(SECRETS_ENDPOINT, SECRETS_REGION);
        AWSSecretsManagerClientBuilder clientBuilder = AWSSecretsManagerClientBuilder.standard();
        clientBuilder.setEndpointConfiguration(config);
        AWSSecretsManager client = clientBuilder.build();

        String secret = "";
        ByteBuffer binarySecretData;
        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(COMPLIANCE_CREDENTIALS_SECRET_NAME).withVersionStage("AWSCURRENT");
        GetSecretValueResult getSecretValueResult = null;
        try {
            getSecretValueResult = client.getSecretValue(getSecretValueRequest);

        } catch(ResourceNotFoundException e) {
            System.out.println("The requested secret " + COMPLIANCE_CREDENTIALS_SECRET_NAME + " was not found");
        } catch (InvalidRequestException e) {
            System.out.println("The request was invalid due to: " + e.getMessage());
        } catch (InvalidParameterException e) {
            System.out.println("The request had invalid params: " + e.getMessage());
        }

        if(getSecretValueResult == null) {
            System.err.println("User secret key password not found");
        }

        // Depending on whether the secret was a string or binary, one of these fields will be populated
        if(getSecretValueResult.getSecretString() != null) {
            secret = getSecretValueResult.getSecretString();
            System.out.println(secret);

        }
        else {
            binarySecretData = getSecretValueResult.getSecretBinary();
            System.out.println(binarySecretData.toString());
        }

        Map<String, String> userCredentials = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            userCredentials = mapper.readValue(secret, Map.class);
        } catch (IOException e){
            System.out.println("Error parsing user credentials: " + e.getMessage());
        }
        return userCredentials;
    }

    public void sendRequest(HttpURLConnection connection, String body) throws IOException {
        DataOutputStream wr = new DataOutputStream (
                connection.getOutputStream());
        wr.writeBytes(body);
        wr.close();
    }

    public StringBuilder getResponse(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        InputStream is = connection.getInputStream();
        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = rd.readLine()) != null) {
            response.append(line);
            response.append('\r');
        }
        rd.close();
        return response;
    }

    public void deadLetterMessage(SNSRecord record){
        // TODO: implement dead lettering
    }

    public void deadLetterMessage(List<SNSRecord> records){
        // TODO: implement dead lettering
    }
}
