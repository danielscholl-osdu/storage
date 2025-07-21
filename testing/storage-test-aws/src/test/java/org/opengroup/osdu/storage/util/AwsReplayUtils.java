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

package org.opengroup.osdu.storage.util;

import com.google.gson.Gson;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.opengroup.osdu.storage.model.AwsReplayStatusResponseHelper;

import java.io.IOException;

public class AwsReplayUtils {

    /**
     * AWS-specific method to handle replay status response with Unix timestamp format
     */
    public static AwsReplayStatusResponseHelper getConvertedReplayStatusResponseFromResponse(CloseableHttpResponse response) throws ProtocolException, IOException {
        String json = EntityUtils.toString(response.getEntity());
        Gson gson = new Gson();
        return gson.fromJson(json, AwsReplayStatusResponseHelper.class);
    }
}
