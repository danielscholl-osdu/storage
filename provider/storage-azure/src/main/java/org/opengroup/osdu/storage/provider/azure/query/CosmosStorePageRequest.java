

package org.opengroup.osdu.storage.provider.azure.query;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class CosmosStorePageRequest extends PageRequest {
    private static final long serialVersionUID = 6093304300037688375L;
    private String requestContinuation;

    public CosmosStorePageRequest(int page, int size, String requestContinuation) {
        super(page, size);
        this.requestContinuation = requestContinuation;
    }

    public static CosmosStorePageRequest of(int page, int size, String requestContinuation) {
        return new CosmosStorePageRequest(page, size, requestContinuation);
    }

    public CosmosStorePageRequest(int page, int size, String requestContinuation, Sort sort) {
        super(page, size, sort);
        this.requestContinuation = requestContinuation;
    }

    public static CosmosStorePageRequest of(int page, int size, String requestContinuation, Sort sort) {
        return new CosmosStorePageRequest(page, size, requestContinuation, sort);
    }

    public String getRequestContinuation() {
        return this.requestContinuation;
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (this.requestContinuation != null ? this.requestContinuation.hashCode() : 0);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CosmosStorePageRequest)) {
            return false;
        } else {
            CosmosStorePageRequest that = (CosmosStorePageRequest)obj;
            boolean continuationTokenEquals = this.requestContinuation != null ? this.requestContinuation.equals(that.requestContinuation) : that.requestContinuation == null;
            return continuationTokenEquals && super.equals(that);
        }
    }
}
