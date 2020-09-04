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

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handler for requests to Lambda function.
 */
public class ComplianceTriggerFunction implements RequestStreamHandler {
    String storageUrl;
    String legalTagChangedUrl;
    ComplianceTriggerFunctionHelper helper = new ComplianceTriggerFunctionHelper();

    public ComplianceTriggerFunction(){
        storageUrl = System.getenv("storageUrl");
        legalTagChangedUrl = System.getenv("legalTagChangedUrl");
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context)  {
        ObjectMapper mapper = new ObjectMapper();
        SNSRequest request = new SNSRequest();
        try {
            request = mapper.readValue(inputStream, SNSRequest.class);
        } catch (Exception e){
            System.err.println(String.format("Legal message failed to parse SNS record: %s", e.getMessage()));
            helper.deadLetterMessage(request.records);
        }

        String jwt = "";
        boolean isAuth = true;
        try {
            jwt = helper.getAccessToken();
        } catch (Exception e){
            System.err.println(String.format("Legal message processor failed to retrieve JWT: %s", e.getMessage()));
            helper.deadLetterMessage(request.records);
            isAuth = false;
        }

        if (isAuth) {
            for (SNSRecord record : request.records) {
                try {
                    MessageContent content = new MessageContent();
                    content.data = Base64.getEncoder().encodeToString(record.sns.message.getBytes());
                    content.attributes = new HashMap<>();
                    content.attributes.put("account-id", record.sns.messageAttributes.accountId.value);
                    content.attributes.put("data-partition-id", record.sns.messageAttributes.dataPartitionId.value);
                    content.attributes.put("correlation-id", record.sns.messageAttributes.correlationId.value);
                    content.messageId = record.sns.messageId;
                    content.publishTime = record.sns.timestamp;

                    Map<String, MessageContent> message = new HashMap<>();
                    message.put("message", content);

                    String body = mapper.writeValueAsString(message);

                    Map<String, String> headers = new HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("data-partition-id", record.sns.messageAttributes.dataPartitionId.value);
                    headers.put("Authorization", jwt);

                    String targetUrl = storageUrl + legalTagChangedUrl;
                    HttpURLConnection connection = getConnection(body, headers, targetUrl);

                    helper.sendRequest(connection, body);

                    helper.getResponse(connection);
                } catch (MalformedURLException e) {
                    System.err.println(String.format("Legal message had a malformed url exception during process: %s", e.getMessage()));
                    helper.deadLetterMessage(record);
                } catch (ProtocolException e) {
                    System.err.println(String.format("Legal message had a protocol exception during process: %s", e.getMessage()));
                    helper.deadLetterMessage(record);
                } catch (IOException e) {
                    System.err.println(String.format("Legal message had an io exception during process: %s", e.getMessage()));
                    helper.deadLetterMessage(record);
                } catch (Exception e) {
                    System.err.println(String.format("Legal message errored during process: %s", e.getMessage()));
                    helper.deadLetterMessage(record);
                }
            }
        }
    }

    public HttpURLConnection getConnection(String body, Map<String, String> headers, String targetURL) throws IOException {
        URL url = new URL(targetURL);
        HttpURLConnection connection =  (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Length",
                Integer.toString(body.getBytes().length));
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");


        for(Map.Entry<String, String> entry: headers.entrySet()){
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }

        connection.setUseCaches(false);
        connection.setDoOutput(true);
        return connection;
    }
}
