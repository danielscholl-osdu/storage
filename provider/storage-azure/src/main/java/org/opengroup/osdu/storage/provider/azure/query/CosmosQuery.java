package org.opengroup.osdu.storage.provider.azure.query;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

public class CosmosQuery {
    private String query = "";
    private Sort sort = Sort.unsorted();
    private Pageable pageable = null;

    public CosmosQuery() {
    }

    public CosmosQuery with(@NonNull Sort sort) {
        if (sort.isSorted()) {
            this.sort = sort.and(this.sort);
        }

        return this;
    }

    public CosmosQuery with(@NonNull Pageable pageable) {
        Assert.notNull(pageable, "pageable should not be null");
        this.pageable = pageable;
        return this;
    }

    public CosmosQuery with(@NonNull String query) {
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

