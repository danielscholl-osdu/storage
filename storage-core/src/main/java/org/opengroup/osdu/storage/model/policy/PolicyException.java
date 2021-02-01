package org.opengroup.osdu.storage.model.policy;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.opengroup.osdu.core.common.http.HttpResponse;
import org.opengroup.osdu.core.common.model.http.DpsException;

@Data
@EqualsAndHashCode(callSuper = false)
public class PolicyException extends DpsException {

    public PolicyException(String message, HttpResponse httpResponse) {
        super(message, httpResponse);
    }
}
