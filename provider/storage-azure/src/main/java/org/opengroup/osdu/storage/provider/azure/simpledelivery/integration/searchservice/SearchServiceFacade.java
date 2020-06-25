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

package org.opengroup.osdu.storage.provider.azure.simpledelivery.integration.searchservice;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import lombok.extern.java.Log;
import org.opengroup.osdu.core.common.http.HttpClient;
import org.opengroup.osdu.core.common.http.HttpRequest;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.search.QueryResponse;
import org.opengroup.osdu.storage.provider.azure.simpledelivery.domain.FileSrnInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/* finds the following info for a File-type SRN:
 *      * its Kind / Schema
 *      * its FileSource (the exact Blob location for the underlying data
 */
@Log
@Component
public class SearchServiceFacade {

    @Inject
    private DpsHeaders headers;

    @Value("${search_service_endpoint}")
    private String searchServiceEndpoint;

    public FileSrnInfo findInfoForFileSrn(String srn) {

        FileSrnInfo fileSrnInfo = new FileSrnInfo();
        try {
            SearchQuery searchQuery = generateQuery(srn);
            List<Map<String, Object>> results = runQuery(searchQuery);
            copyResultsToOutput(srn, fileSrnInfo, results);
        } catch (Exception error) {
            log.log(Level.SEVERE,
                    "There was an error when querying Search svc for the following SRN: " + srn,
                    error);
        }
        return fileSrnInfo;
    }

    private List<Map<String, Object>> runQuery(SearchQuery searchQuery) {
        HttpClient httpClient = new HttpClient();
        HttpRequest request = HttpRequest.post(searchQuery)
                .url(searchServiceEndpoint)
                .headers(headers.getHeaders())
                .build();
        HttpResponse result = httpClient.send(request);

        String body = result.getBody();
        QueryResponse response = new Gson().fromJson(body, QueryResponse.class);

        return response.getResults();
    }

    private SearchQuery generateQuery(String srn) {
        //construct kind from PartitionId  e.g., opendes:*:*:*
        String partitionId = headers.getPartitionId();
        String kind = String.format("%s:*:*:*", partitionId);

        SearchQuery query = new SearchQuery();
        query.setKind(kind);
        query.setLimit(10); //TODO: is this a sensible limit?

        //return only where SRN matches AND there is a FileSource attribute
        query.setQuery("data.ResourceID:\"" + srn + "\" " +
                "AND _exists_:data.Data.GroupTypeProperties.PreLoadFilePath");
        return query;
    }

    private void copyResultsToOutput(String srn, FileSrnInfo fileSrnInfo, List<Map<String, Object>> results) {
        if (results.size() <= 0) {
            log.log(Level.WARNING,
                    "Search svc returned no results when searching for the following SRN: " + srn);
            return;
        }
        Map<String, Object> theFirstResult = results.get(0);

        Object resultData = theFirstResult.get("data");
        Object resultKind = theFirstResult.get("kind");
        LinkedTreeMap<String, Object> lkm = (LinkedTreeMap<String, Object>) resultData;
        Object fileSource = lkm.get("Data.GroupTypeProperties.PreLoadFilePath"); //get value of nested object in LinkedTreeMap [0].data.[key]
        fileSrnInfo.kind = resultKind.toString();
        fileSrnInfo.fileSource = fileSource.toString();
        fileSrnInfo.isContainer = isSrnForContainer(srn);
    }


    /*
     temporarily parse the srn to see if it has file/ovds  or file/zgy
     @todo replace with better logic!! Remove hardcoded reference to "ovds".
     @todo needs external rules to map srn type to access pattern, ex, using the ResourceTypeID which is not currently passed in
    */
    private boolean isSrnForContainer(String srn) {
        boolean result = false;

        if (srn != null && srn.contains("ovds")) {
            result = true;
        }

        return result;
    }

}
