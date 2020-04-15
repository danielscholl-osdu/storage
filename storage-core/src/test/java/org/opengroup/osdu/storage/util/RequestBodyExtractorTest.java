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

import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.http.RequestBodyExtractor;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RequestBodyExtractorTest {
    private static String REQUEST_BODY = "{\"message\":{\"messageId\":\"unit-test-message-id\",\"data\":\"eyJzdGF0dXNDaGFuZ2VkVGFncyI6W3siY2hhbmdlZFRhZ05hbWUiOiJ0YWcxIiwiY2hhbmdlZFRhZ1N0YXR1cyI6ImluY29tcGxpYW50In0seyJjaGFuZ2VkVGFnTmFtZSI6InRhZzIiLCJjaGFuZ2VkVGFnU3RhdHVzIjoiaW5jb21wbGlhbnQifV19\",\"attributes\":{\"account-id\":\"test-tenant\",\"test-user\":\"unittest@gmail.com\"}}}";
    private static String REQUEST_BODY_DP = "{\"message\":{\"messageId\":\"unit-test-message-id\",\"data\":\"eyJzdGF0dXNDaGFuZ2VkVGFncyI6W3siY2hhbmdlZFRhZ05hbWUiOiJ0YWcxIiwiY2hhbmdlZFRhZ1N0YXR1cyI6ImluY29tcGxpYW50In0seyJjaGFuZ2VkVGFnTmFtZSI6InRhZzIiLCJjaGFuZ2VkVGFnU3RhdHVzIjoiaW5jb21wbGlhbnQifV19\",\"attributes\":{\"data-partition-id\":\"test-tenant\",\"test-user\":\"unittest@gmail.com\"}}}";
    private static String REQUEST_BODY_NOTENANT = "{\"message\":{\"messageId\":\"unit-test-message-id\",\"data\":\"eyJzdGF0dXNDaGFuZ2VkVGFncyI6W3siY2hhbmdlZFRhZ05hbWUiOiJ0YWcxIiwiY2hhbmdlZFRhZ1N0YXR1cyI6ImluY29tcGxpYW50In0seyJjaGFuZ2VkVGFnTmFtZSI6InRhZzIiLCJjaGFuZ2VkVGFnU3RhdHVzIjoiaW5jb21wbGlhbnQifV19\",\"attributes\":{\"account-id\":\"\",\"test-user\":\"unittest@gmail.com\"}}}";
    private static String NON_JSON_REQUEST_BODY = "{\"request body\":\"request body is not a json object.\"}";

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private BufferedReader bufferReader;

    @InjectMocks
    private RequestBodyExtractor sut;

    @Test
    public void should_returnAttributes_whenRequestBodyProvided() throws Exception {
        this.createRequestStream(REQUEST_BODY);
        when(this.httpServletRequest.getRequestURI()).thenReturn("legaltag-changed");
        when(this.httpServletRequest.getReader()).thenReturn(this.bufferReader);
        Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put(DpsHeaders.ACCOUNT_ID, "test-tenant");
        expectedAttributes.put("test-user", "unittest@gmail.com");

        Map<String, String> attributes = this.sut.extractAttributesFromRequestBody();
        Assert.assertEquals(expectedAttributes, attributes);
    }

    @Test
    public void should_returnAttributes_whenRequestBodyProvided_dp() throws Exception {
        this.createRequestStream(REQUEST_BODY_DP);
        when(this.httpServletRequest.getRequestURI()).thenReturn("legaltag-changed");
        when(this.httpServletRequest.getReader()).thenReturn(this.bufferReader);
        Map<String, String> expectedAttributes = new HashMap<>();
        expectedAttributes.put(DpsHeaders.DATA_PARTITION_ID, "test-tenant");
        expectedAttributes.put("test-user", "unittest@gmail.com");

        Map<String, String> attributes = this.sut.extractAttributesFromRequestBody();
        Assert.assertEquals(expectedAttributes, attributes);
    }

    @Test
    public void should_throwError_whenRequestBodyProvided_with_notenant() throws Exception {
        this.createRequestStream(REQUEST_BODY_NOTENANT);
        when(this.httpServletRequest.getRequestURI()).thenReturn("legaltag-changed");
        when(this.httpServletRequest.getReader()).thenReturn(this.bufferReader);

        try {
            this.sut.extractAttributesFromRequestBody();
            Assert.fail("Expected exception");
        }catch(AppException e){
            Assert.assertEquals(400, e.getError().getCode());
        }

    }

    @Test
    public void should_returnData_whenRequestBodyProvided() throws Exception {
        this.createRequestStream(REQUEST_BODY);
        when(this.httpServletRequest.getRequestURI()).thenReturn("legaltag-changed");
        when(this.httpServletRequest.getReader()).thenReturn(this.bufferReader);
        String expectedData = "{\"statusChangedTags\":[{\"changedTagName\":\"tag1\",\"changedTagStatus\":\"incompliant\"},{\"changedTagName\":\"tag2\",\"changedTagStatus\":\"incompliant\"}]}";

        String data = this.sut.extractDataFromRequestBody();
        Assert.assertEquals(expectedData, data);
    }

    @Test(expected = AppException.class)
    public void should_throwException_whenRequestBodyIsNotPubsubEndpoint() throws Exception {
        this.createRequestStream(NON_JSON_REQUEST_BODY);
        when(this.httpServletRequest.getReader()).thenReturn(this.bufferReader);
        this.sut.extractAttributesFromRequestBody();
    }

    private void createRequestStream(String requestBody) {
        String[] requestBodies = { requestBody };
        Stream<String> stringStream = Stream.of(requestBodies);
        when(this.bufferReader.lines()).thenReturn(stringStream);
    }
}