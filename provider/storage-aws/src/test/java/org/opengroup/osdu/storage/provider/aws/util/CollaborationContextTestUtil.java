package org.opengroup.osdu.storage.provider.aws.util;

import org.opengroup.osdu.core.common.model.http.CollaborationContext;

import java.util.Collections;
import java.util.UUID;

public class CollaborationContextTestUtil {

    public static CollaborationContext getACollaborationContext() {
        final UUID id = UUID.randomUUID();
        final String application = "application";
        return new CollaborationContext(id, application, Collections.emptyMap());
    }

}
