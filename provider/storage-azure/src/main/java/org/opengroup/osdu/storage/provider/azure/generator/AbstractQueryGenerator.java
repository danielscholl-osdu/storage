package org.opengroup.osdu.storage.provider.azure.generator;

import com.azure.cosmos.SqlQuerySpec;

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