package org.opengroup.osdu.storage.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.util.CollaborationContextUtil;

import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class CollaborationUtilTest {

    private final static String RECORD_ID = "opendes:id:15706318658560";
    @InjectMocks
    private CollaborationUtil collaborationUtil;

    @Test
    public void shouldGetCorrectIdWithNamespace_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext= CollaborationContext.builder().id(CollaborationId).application("unit testing").build();
        
        String result = CollaborationUtil.getIdWithNamespace(RECORD_ID, Optional.of(collaborationContext));

        assertEquals(result,CollaborationId.toString()+RECORD_ID);
    }

    @Test
    public void shouldGetOnlyId_IfEmptyCollaborationContextIsProvided() {
        String result = CollaborationUtil.getIdWithNamespace(RECORD_ID, Optional.empty());

        assertEquals(result, RECORD_ID);
    }
}
