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

import org.junit.Test;
import org.mockito.Mockito;
import com.amazonaws.serverless.proxy.internal.testutils.MockLambdaContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ComplianceTriggerFunctionTest {
  @Test
  public void successfulResponse() throws IOException {
      // arrange
      String request = "{\n" +
              "  \"Records\": [\n" +
              "    {\n" +
              "      \"EventSource\": \"aws:sns\",\n" +
              "      \"EventVersion\": \"1.0\",\n" +
              "      \"EventSubscriptionArn\": \"arn:{partition}:sns:EXAMPLE\",\n" +
              "      \"Sns\": {\n" +
              "        \"Type\" : \"Notification\",\n" +
              "        \"MessageId\" : \"820aa187-771d-5afe-80c7-f7d7bdf8006c\",\n" +
              "        \"TopicArn\" : \"arn:aws:sns:us-east-1:888733619319:dev-osdu-legal-messages\",\n" +
              "        \"Message\" : \"{\\\"statusChangedTags\\\":[{\\\"changedTagName\\\":\\\"opendes-gae-integration-test-1574357156646\\\",\\\"changedTagStatus\\\":\\\"incompliant\\\"}]}\",\n" +
              "        \"Timestamp\" : \"2019-11-21T17:25:57.249Z\",\n" +
              "        \"SignatureVersion\" : \"1\",\n" +
              "        \"Signature\" : \"qYwdEu7MDfy2r+m1/57LpEpfQAJMLDxoXstdQj1vG51cxWdzl5mYGt6I+/fevUS0ntgqa0ea4uGRNGo1LF+8jKBd4Be2lYLyEgwIMOMALkwNxG239Xk+c9fJV4XvNaVwkky84hXGlIUeJkAzh7V1+fappVWkAeGW36ForcYEMNMGiNab0PDNd5lnJdNZTZB3MzLu8PNR/7DaA2mf6q8xBPOuLSLzjQEIEsF4Ar/A2sDzVVylUIqnUocTp9RzcmZhHSpeQVzpm0xg4csmy7U5yIIEjkmDM69KZf++zt9H/uBNEv732L+bEYjH/YzJHoOeA+VL1euQ+kRLU1pz+BRieQ==\",\n" +
              "        \"SigningCertURL\" : \"https://sns.us-east-1.amazonaws.com/SimpleNotificationService-6aad65c2f9911b05cd53efda11f913f9.pem\",\n" +
              "        \"UnsubscribeURL\" : \"https://sns.us-east-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:us-east-1:888733619319:dev-osdu-legal-messages:8e9641e2-bae8-43a8-9e68-9544d7b987a6\",\n" +
              "        \"MessageAttributes\" : {\n" +
              "          \"account-id\" : {\"Type\":\"String\",\"Value\":\"opendes\"},\n" +
              "          \"correlation-id\" : {\"Type\":\"String\",\"Value\":\"79ad57ac-2d14-4a95-a4be-55fb584fa2b6\"},\n" +
              "          \"data-partition-id\" : {\"Type\":\"String\",\"Value\":\"opendes\"}\n" +
              "        }\n" +
              "      }\n" +
              "    }\n" +
              "  ]\n" +
              "}";

      InputStream stream = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
      ComplianceTriggerFunction function = new ComplianceTriggerFunction();

      function.helper = Mockito.mock(ComplianceTriggerFunctionHelper.class);
      function.storageUrl = "http://localhost:8081/storage/api/v2/";
      function.legalTagChangedUrl = "test-url";

      ByteArrayOutputStream out = new ByteArrayOutputStream();

      String expectedBody = "{\"message\":{\"messageId\":\"820aa187-771d-5afe-80c7-f7d7bdf8006c\",\"data\":\"eyJzdGF0dXNDaGFuZ2VkVGFncyI6W3siY2hhbmdlZFRhZ05hbWUiOiJvcGVuZGVzLWdhZS1pbnRlZ3JhdGlvbi10ZXN0LTE1NzQzNTcxNTY2NDYiLCJjaGFuZ2VkVGFnU3RhdHVzIjoiaW5jb21wbGlhbnQifV19\",\"publishTime\":\"2019-11-21T17:25:57.249Z\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"79ad57ac-2d14-4a95-a4be-55fb584fa2b6\",\"data-partition-id\":\"opendes\"}}}";

      // act
      function.handleRequest(stream, out, new MockLambdaContext());

      // assert
      Mockito.verify(function.helper, Mockito.times(1)).getAccessToken();
      Mockito.verify(function.helper, Mockito.times(1)).sendRequest(Mockito.any(), Mockito.eq(expectedBody));
  }

    @Test
    public void failureResponse() throws IOException {
        // arrange
        String request = "{\n" +
                "  \"Records\": [\n" +
                "    {\n" +
                "      \"EventSource\": \"aws:sns\",\n" +
                "      \"EventVersion\": \"1.0\",\n" +
                "      \"EventSubscriptionArn\": \"arn:{partition}:sns:EXAMPLE\",\n" +
                "      \"Sns\": {\n" +
                "        \"Type\" : \"Notification\",\n" +
                "        \"MessageId\" : \"820aa187-771d-5afe-80c7-f7d7bdf8006c\",\n" +
                "        \"TopicArn\" : \"arn:aws:sns:us-east-1:888733619319:dev-osdu-legal-messages\",\n" +
                "        \"Message\" : \"{\\\"statusChangedTags\\\":[{\\\"changedTagName\\\":\\\"opendes-gae-integration-test-1574357156646\\\",\\\"changedTagStatus\\\":\\\"incompliant\\\"}]}\",\n" +
                "        \"Timestamp\" : \"2019-11-21T17:25:57.249Z\",\n" +
                "        \"SignatureVersion\" : \"1\",\n" +
                "        \"Signature\" : \"qYwdEu7MDfy2r+m1/57LpEpfQAJMLDxoXstdQj1vG51cxWdzl5mYGt6I+/fevUS0ntgqa0ea4uGRNGo1LF+8jKBd4Be2lYLyEgwIMOMALkwNxG239Xk+c9fJV4XvNaVwkky84hXGlIUeJkAzh7V1+fappVWkAeGW36ForcYEMNMGiNab0PDNd5lnJdNZTZB3MzLu8PNR/7DaA2mf6q8xBPOuLSLzjQEIEsF4Ar/A2sDzVVylUIqnUocTp9RzcmZhHSpeQVzpm0xg4csmy7U5yIIEjkmDM69KZf++zt9H/uBNEv732L+bEYjH/YzJHoOeA+VL1euQ+kRLU1pz+BRieQ==\",\n" +
                "        \"SigningCertURL\" : \"https://sns.us-east-1.amazonaws.com/SimpleNotificationService-6aad65c2f9911b05cd53efda11f913f9.pem\",\n" +
                "        \"UnsubscribeURL\" : \"https://sns.us-east-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:us-east-1:888733619319:dev-osdu-legal-messages:8e9641e2-bae8-43a8-9e68-9544d7b987a6\",\n" +
                "        \"MessageAttributes\" : {\n" +
                "          \"account-id\" : {\"Type\":\"String\",\"Value\":\"opendes\"},\n" +
                "          \"correlation-id\" : {\"Type\":\"String\",\"Value\":\"79ad57ac-2d14-4a95-a4be-55fb584fa2b6\"},\n" +
                "          \"data-partition-id\" : {\"Type\":\"String\",\"Value\":\"opendes\"}\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        InputStream stream = new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
        ComplianceTriggerFunction function = new ComplianceTriggerFunction();

        function.helper = Mockito.mock(ComplianceTriggerFunctionHelper.class);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String expectedBody = "{\"message\":{\"messageId\":\"820aa187-771d-5afe-80c7-f7d7bdf8006c\",\"data\":\"eyJzdGF0dXNDaGFuZ2VkVGFncyI6W3siY2hhbmdlZFRhZ05hbWUiOiJvcGVuZGVzLWdhZS1pbnRlZ3JhdGlvbi10ZXN0LTE1NzQzNTcxNTY2NDYiLCJjaGFuZ2VkVGFnU3RhdHVzIjoiaW5jb21wbGlhbnQifV19\",\"publishTime\":\"2019-11-21T17:25:57.249Z\",\"attributes\":{\"account-id\":\"opendes\",\"correlation-id\":\"79ad57ac-2d14-4a95-a4be-55fb584fa2b6\",\"data-partition-id\":\"opendes\"}}}";

        // act
        function.handleRequest(stream, out, new MockLambdaContext());

        // assert
        Mockito.verify(function.helper, Mockito.times(1)).deadLetterMessage(Mockito.any(SNSRecord.class));
    }
}
