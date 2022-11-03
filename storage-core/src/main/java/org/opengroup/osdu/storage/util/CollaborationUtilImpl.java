package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.storage.util.api.CollaborationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CollaborationUtilImpl implements CollaborationUtil {
    
    @Autowired
    private CollaborationContext collaborationContext;

    public CollaborationUtilImpl(CollaborationContext collaborationContext) {
        this.collaborationContext = collaborationContext;
    }

    @Override
    public String getIdWithNamespace(String recordId) {
        return getIdWithNamespace(recordId, collaborationContext.getId());
    }
    
    @Override
    public String getIdWithNamespace(String recordId, String namespace) {
        if(Strings.isNullOrEmpty(namespace))
            return recordId;
        return recordId + namespace;
    }
    
    @Override
    public String getNamespaceFromCollaborationContext(){
        return collaborationContext.getId();
    }
}
