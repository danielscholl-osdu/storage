// Copyright © Microsoft Corporation
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

package org.opengroup.osdu.storage.simpledelivery;

import com.sun.jersey.api.client.ClientResponse;
import org.junit.*;
import org.opengroup.osdu.storage.util.*;
import static org.junit.Assert.assertEquals;

public class TestGetFilesSignedUrlNegative {

    private static AzureTestUtils azureTestUtils = new AzureTestUtils();

    @Test
    public void should_receiveHttp400_when_requestingSRNsWithInvalidInput() throws Exception {
        String jsonInput = "invalid input";

        ClientResponse response = TestUtils.send("delivery/GetFileSignedURL", "POST", HeaderUtils.getHeaders(TenantUtils.getTenantName(), azureTestUtils.getToken()), jsonInput, "");

        assertEquals(400, response.getStatus());
    }

    private String generatePath(String accountName, String containerName, String blobName) {
        return String.format("https://%s.blob.core.windows.net/%s/%s", accountName, containerName, blobName);
    }
}
