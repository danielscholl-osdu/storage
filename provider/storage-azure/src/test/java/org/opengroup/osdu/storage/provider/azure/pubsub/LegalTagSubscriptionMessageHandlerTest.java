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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalTagSubscriptionMessageHandlerTest {
    private static final UUID uuid = UUID.randomUUID();

    @InjectMocks
    private LegalTagSubscriptionMessageHandler legalTagSubscriptionMessageHandler;
    @Mock
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

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
        doThrow(new RuntimeException()).when(legalComplianceChangeUpdate).updateCompliance(message);
        legalTagSubscriptionMessageHandler.onMessageAsync(message);
        verify(subscriptionClient, times(1)).abandonAsync(uuid);
        verify(legalComplianceChangeUpdate, times(1)).updateCompliance(message);
    }
}
