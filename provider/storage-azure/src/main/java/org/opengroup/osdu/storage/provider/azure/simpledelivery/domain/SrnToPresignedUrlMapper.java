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

package org.opengroup.osdu.storage.provider.azure.simpledelivery.domain;

import lombok.extern.java.Log;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.api.payloads.GetResourcesResponsePayload;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.integration.searchservice.SearchServiceFacade;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.integration.blob.BlobSasTokenFacade;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.logging.Level;

@Log
@Component
public class SrnToPresignedUrlMapper {

    @Inject
    private BlobSasTokenFacade tokenFacade;

    @Inject
    private SearchServiceFacade searchFacade;

    public GetResourcesResponsePayload processAllSrns(List<String> input) {
        GetResourcesResponsePayload out = new GetResourcesResponsePayload();

        // @todo consider revisiting this, this is rather chatty, making a single query for each srn

        for (String srn : input) {
            try {
                FileSrnInfo info = searchFacade.findInfoForFileSrn(srn);

                String fileSource = info.getFileSource();
                String signedUrl;

                if (null == fileSource) {
                    out.appendUnprocessedSrn(srn);
                    continue;
                }

                if (info.isContainer) {
                    signedUrl = tokenFacade.signContainer(fileSource);
                }
                else {
                    signedUrl = tokenFacade.sign(fileSource);
                }

                if (null == signedUrl) {
                    out.appendUnprocessedSrn(srn);
                    continue;
                }
                out.appendSignedSrnSigned(srn, info, signedUrl);
            } catch (Exception error) {
                log.log(Level.SEVERE,
                        "An unexpected error occurred while processing the following SRN: " + srn,
                        error);
                out.appendUnprocessedSrn(srn);
            }
        }
        return out;
    }
}
