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

package org.opengroup.osdu.storage.provider.azure.query;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

public class CosmosStoreQuery {
    private String query = "";
    private Sort sort = Sort.unsorted();
    private Pageable pageable = null;

    public CosmosStoreQuery() {
    }

    public CosmosStoreQuery with(@NonNull Sort sort) {
        if (sort.isSorted()) {
            this.sort = sort.and(this.sort);
        }

        return this;
    }

    public CosmosStoreQuery with(@NonNull Pageable pageable) {
        Assert.notNull(pageable, "pageable should not be null");
        this.pageable = pageable;
        return this;
    }

    public CosmosStoreQuery with(@NonNull String query) {
        Assert.notNull(query, "query should not be null");
        this.query = query;
        return this;
    }

    public String getQuery() {
        return this.query;
    }

    public Sort getSort() {
        return this.sort;
    }

    public Pageable getPageable() {
        return this.pageable;
    }
}

