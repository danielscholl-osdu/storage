package org.opengroup.osdu.storage.util;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;

import java.util.Optional;

public class CollaborationUtil {
    
    public static String getIdWithNamespace(String recordId, Optional<CollaborationContext> collaborationContext) {
        if (!collaborationContext.isPresent())
            return recordId;
        return collaborationContext.get().getId() + recordId;
    }

    public static String getIdWithoutNamespace(String recordId, Optional<CollaborationContext> collaborationContext) {
        if (!collaborationContext.isPresent())
            return recordId;
        return recordId.substring(collaborationContext.get().getId().length());
    }

    public static String getNamespace(Optional<CollaborationContext> collaborationContext) {
        if (collaborationContext.isPresent())
            return collaborationContext.get().getId();
        return "";
    }
}
