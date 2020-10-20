package org.opengroup.osdu.storage.provider.reference.util;

import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class ServiceAccountJwtClientImpl implements IServiceAccountJwtClient {
    @Override
    public String getIdToken(String tenantName){
        return "dont-have-one";
    }
}
