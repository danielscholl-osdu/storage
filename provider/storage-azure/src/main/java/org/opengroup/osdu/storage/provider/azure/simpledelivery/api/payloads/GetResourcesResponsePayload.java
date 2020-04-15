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

package org.opengroup.osdu.storage.provider.azure.simpledelivery.api.payloads;

import lombok.Data;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.domain.FileSrnInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class GetResourcesResponsePayload {
    private List<String> unprocessed = new ArrayList<>();
    private Map<String, ProcessedSrnResult> processed = new HashMap<>();

    public void appendUnprocessedSrn(String srn) {
        this.unprocessed.add(srn);
    }

    public void appendSignedSrnSigned(String srn, FileSrnInfo info, String sign) {
        String fileSource = info.getFileSource();
        String kind = info.getKind();
        ProcessedSrnResult result = new ProcessedSrnResult(sign, fileSource, kind);
        this.processed.put(srn, result);
    }
}
