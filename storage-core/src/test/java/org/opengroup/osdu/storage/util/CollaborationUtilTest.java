package org.opengroup.osdu.storage.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class CollaborationUtilTest {

    private final static String RECORD_ID = "opendes:id:15706318658560";
    @InjectMocks
    private CollaborationUtil collaborationUtil;

    @Test
    public void shouldGetCorrectIdWithNamespace_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).application("unit testing").build();

        String result = CollaborationUtil.getIdWithNamespace(RECORD_ID, Optional.of(collaborationContext));

        assertEquals(result, CollaborationId.toString() + RECORD_ID);
    }

    @Test
    public void shouldGetCorrectIdWithoutNamespace_IfCollaborationContextIsProvided() {
        UUID CollaborationId = UUID.randomUUID();
        CollaborationContext collaborationContext = CollaborationContext.builder().id(CollaborationId).application("unit testing").build();

        String result = CollaborationUtil.getIdWithoutNamespace(collaborationContext.getId() + RECORD_ID, Optional.of(collaborationContext));

        assertEquals(result, RECORD_ID);
    }

    @Test
    public void shouldGetOnlyId_IfEmptyCollaborationContextIsProvided_whenGetIdWithNamespace() {
        String result = CollaborationUtil.getIdWithNamespace(RECORD_ID, Optional.empty());

        assertEquals(result, RECORD_ID);
    }

    @Test
    public void shouldGetOnlyId_IfEmptyCollaborationContextIsProvided_whenGetIdWithoutNamespace() {
        String result = CollaborationUtil.getIdWithoutNamespace(RECORD_ID, Optional.empty());

        assertEquals(result, RECORD_ID);
    }
}
