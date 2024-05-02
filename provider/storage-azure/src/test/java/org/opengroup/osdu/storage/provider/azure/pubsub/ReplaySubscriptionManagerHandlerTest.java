package org.opengroup.osdu.storage.provider.azure.pubsub;


import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.SubscriptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReplaySubscriptionManagerHandlerTest {

    private static final UUID uuid = UUID.randomUUID();

    @InjectMocks
    private ReplaySubscriptionMessageHandler replaySubscriptionMessageHandler;
    @Mock
    private ReplayMessageHandler replayMessageHandler;

    @Mock
    private SubscriptionClient subscriptionClient;

    @Mock
    private Message message;

    @BeforeEach
    void init() {
        when(message.getLockToken()).thenReturn(uuid);
    }

    @Test
    void shouldInvokeAbandonAsync() throws Exception {

        doThrow(new RuntimeException()).when(replayMessageHandler).handle(message);
        replaySubscriptionMessageHandler.onMessageAsync(message);
        verify(subscriptionClient, times(1)).abandonAsync(uuid);
        verify(replayMessageHandler, times(1)).handle(message);
    }
}
