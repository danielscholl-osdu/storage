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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class SearchQuery {

    private String kind;
    private Integer offset;
    private Integer limit;
    private String query;
    private Boolean queryAsOwner;
    private String aggregateBy;
    private List<String> returnedFields;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
