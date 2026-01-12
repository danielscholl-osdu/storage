package org.opengroup.osdu.storage.api;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.http.CollaborationContextFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.request.ReplayRequest;
import org.opengroup.osdu.storage.response.ReplayResponse;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
public class ReplayApiTest {

    private final String USER = "user";
    private final String TENANT = "tenant1";
    private final String COLLABORATION_DIRECTIVES = "id=9e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=TestApp";
    private final Optional<CollaborationContext> COLLABORATION_CONTEXT = Optional.ofNullable(CollaborationContext.builder().id(UUID.fromString("9e1c4e74-3b9b-4b17-a0d5-67766558ec65")).application("TestApp").build());

    @Mock
    private CollaborationContextFactory collaborationContextFactory;

    @InjectMocks
    private ReplayApi sut;

    @Mock
    private DpsHeaders httpHeaders;

    @BeforeEach
    public void setup() {

        initMocks(this);
        lenient().when(this.httpHeaders.getUserEmail()).thenReturn(this.USER);
        lenient().when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(Optional.empty());
        TenantInfo tenant = new TenantInfo();
        tenant.setName(this.TENANT);
    }

    @Test
    public void should_returnsHttp501_when_creatingReplayRequestWithCollaborationHeader() {

        ReplayRequest replayRequest = new ReplayRequest();
        replayRequest.setOperation("replay");
        lenient().when(this.collaborationContextFactory.create(eq(COLLABORATION_DIRECTIVES))).thenReturn(COLLABORATION_CONTEXT);
        try {
            ResponseEntity<ReplayResponse> response = this.sut.triggerReplay(COLLABORATION_DIRECTIVES, replayRequest);
        } catch (AppException e) {
            assertEquals(501, e.getError().getCode());
            assertEquals("Collaboration feature not implemented for Replay API.", e.getError().getReason());
            assertEquals("Collaboration feature is not yet supported for the Replay API.", e.getError().getMessage());
        }
    }

}
