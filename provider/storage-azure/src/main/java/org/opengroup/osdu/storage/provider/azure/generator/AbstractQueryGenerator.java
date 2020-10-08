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

package org.opengroup.osdu.storage.provider.azure.generator;

import com.azure.cosmos.models.SqlQuerySpec;
import org.opengroup.osdu.storage.provider.azure.query.CosmosStoreQuery;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractQueryGenerator {

    private String getParameter(@NonNull Sort.Order order) {
        Assert.isTrue(!order.isIgnoreCase(), "Ignore case is not supported");
        String direction = order.isDescending() ? "DESC" : "ASC";
        return String.format("c.%s %s", order.getProperty(), direction);
    }

    private String generateQuerySort(@NonNull Sort sort) {
        if (sort.isUnsorted()) {
            return "";
        } else {
            String queryTail = "ORDER BY";
            List<String> subjects = (List)sort.stream().map(this::getParameter).collect(Collectors.toList());
            return "ORDER BY " + String.join(",", subjects);
        }
    }

    @NonNull
    private String generateQueryTail(@NonNull CosmosStoreQuery query) {
        List<String> queryTails = new ArrayList();
        queryTails.add(this.generateQuerySort(query.getSort()));
        return String.join(" ", (Iterable)queryTails.stream().filter(StringUtils::hasText).collect(Collectors.toList()));
    }

    protected SqlQuerySpec generateQuery(@NonNull CosmosStoreQuery query, @NonNull String queryHead) {
        Assert.hasText(queryHead, "query head should have text.");
        String queryString = String.join(" ", queryHead, this.generateQueryTail(query));
        return new SqlQuerySpec(queryString);
    }

    protected SqlQuerySpec generateCosmosQuery(@NonNull CosmosStoreQuery query, @NonNull String queryHead) {
        Assert.hasText(queryHead, "query head should have text.");
        String queryString = String.join(" ", queryHead, this.generateQueryTail(query));
        return new SqlQuerySpec(queryString);
    }

    protected AbstractQueryGenerator() {
    }
}