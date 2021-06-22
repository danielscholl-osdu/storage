package org.opengroup.osdu.storage.provider.azure.pubsub;


import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.SubscriptionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class LegalTagSubscriptionMessageHandlerTest {
    private static final UUID uuid = UUID.randomUUID();
    private static Map<String, LegalCompliance> output = new HashMap<>();

    @InjectMocks
    private LegalTagSubscriptionMessageHandler messageHandler;

    @Mock
    private LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure;

    @Mock
    private SubscriptionClient subscriptionClient;

    @Mock
    private Message message;

    @BeforeEach
    public void init() {
        when(message.getLockToken()).thenReturn(uuid);
    }

    @Test
    public void shouldInvokeCompleteAsync() {
        legalComplianceChangeServiceAzure.updateCompliance(message);
        messageHandler.onMessageAsync(message);
        verify(subscriptionClient, times(1)).completeAsync(uuid);
        verify(legalComplianceChangeServiceAzure, times(2)).updateCompliance(message);
    }
}
