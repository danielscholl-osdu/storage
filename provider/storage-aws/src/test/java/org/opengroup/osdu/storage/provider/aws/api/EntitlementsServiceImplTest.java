// Copyright © Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws.api;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;
import org.opengroup.osdu.core.aws.entitlements.EntitlementsServiceAwsImpl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.entitlements.EntitlementsException;
import org.opengroup.osdu.core.common.model.entitlements.GroupInfo;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.aws.entitlements.EntitlementsServiceHelper;
import org.opengroup.osdu.core.aws.entitlements.GroupsRequest;
import org.opengroup.osdu.core.aws.lambda.HttpMethods;
import org.opengroup.osdu.core.aws.lambda.LambdaConfig;
import org.opengroup.osdu.storage.StorageApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes={StorageApplication.class})
public class EntitlementsServiceImplTest {

    @Test
    public void getGroupsTest() throws JsonProcessingException, UnsupportedEncodingException, EntitlementsException {
        // arrange
        String getGroupsFunctionName = "mocked-group-function";
        DpsHeaders headers = new DpsHeaders();
        LambdaConfig config = Mockito.mock(LambdaConfig.class);
        AWSLambda lambda = Mockito.mock(AWSLambda.class);

        GroupsRequest request = new GroupsRequest();
        request.body = "";
        request.headers = headers.getHeaders();
        request.httpMethod = HttpMethods.GET;
        ObjectMapper mapper = new ObjectMapper();
        String expectedPayload = mapper.writeValueAsString(request);

        String resp = "{\n" +
                "  \"isBase64Encoded\" : false,\n" +
                "  \"statusCode\" : 200,\n" +
                "  \"body\" : \"[{\\\"groupUniqueIdentifier\\\":null,\\\"groupEmail\\\":\\\"test-email\\\",\\\"name\\\":\\\"test-name\\\",\\\"description\\\":\\\"test-description\\\"}]\",\n" +
                "  \"headers\" : {\n" +
                "    \"user\" : \"test-email@testing.com\"\n" +
                "  }\n" +
                "}";

        InvokeRequest invokeRequest = new InvokeRequest();
        invokeRequest.setFunctionName(getGroupsFunctionName); // Lambda function name identifier
        invokeRequest.setInvocationType(InvocationType.RequestResponse);
        invokeRequest.setPayload(expectedPayload);
        InvokeResult invokeResult = Mockito.mock(InvokeResult.class);

        Mockito.when(invokeResult.getPayload())
                .thenReturn(ByteBuffer.wrap(resp.getBytes(UTF_8)));

        Mockito.when(lambda.invoke(Mockito.eq(invokeRequest)))
                .thenReturn(invokeResult);

        Mockito.when(config.awsLambda())
                .thenReturn(lambda);

        EntitlementsServiceHelper entitlementsServiceHelper = new EntitlementsServiceHelper(Regions.US_EAST_1, "mocked-group-function");
        Whitebox.setInternalState(entitlementsServiceHelper, "lambda", lambda);

        EntitlementsServiceAwsImpl service = new EntitlementsServiceAwsImpl(headers);
        Whitebox.setInternalState(service, "entitlementsServiceHelper", entitlementsServiceHelper);

        Groups expectedGroups = new Groups();
        List<GroupInfo> expectedGroupList = new ArrayList<>();
        GroupInfo expectedGroup = new GroupInfo();
        expectedGroup.setDescription("test-description");
        expectedGroup.setName("test-name");
        expectedGroup.setEmail("test-email");
        expectedGroupList.add(expectedGroup);
        expectedGroups.setGroups(expectedGroupList);

        // act
        Groups groups = service.getGroups();

        // assert
        Assert.assertEquals(expectedGroups.getGroups().size(), groups.getGroups().size());
        Assert.assertEquals(expectedGroups.getGroups().get(0).getName(), groups.getGroups().get(0).getName());
        Assert.assertEquals(expectedGroups.getGroups().get(0).getEmail(), groups.getGroups().get(0).getEmail());
        Assert.assertEquals(expectedGroups.getGroups().get(0).getDescription(), groups.getGroups().get(0).getDescription());
    }
}
