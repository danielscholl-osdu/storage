package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.PubSubConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LegalTagSubscriptionClientFactoryTest {
    @InjectMocks
    private LegalTagSubscriptionClientFactory subsClientFactory;

    @Mock
    private SubscriptionClient subscriptionClient;

    @Mock
    private ISubscriptionClientFactory subscriptionClientFactory;

    @Mock
    private AzureBootstrapConfig azureBootstrapConfig;

    @Mock
    private PubSubConfig pubSubConfig;

    private static final String sbTopic = "testTopic";
    private static final String sbSubscription = "testSubscription";
    private static final String dataPartition = "testPartition";

    @Test
    public void subscriptionClientShouldNotBeNull() throws ServiceBusException, InterruptedException {
        when(subscriptionClientFactory.getClient(dataPartition, sbTopic, sbSubscription))
                .thenReturn(subscriptionClient);

        SubscriptionClient result = subsClientFactory.getSubscriptionClient(dataPartition, sbTopic, sbSubscription);
        assertNotNull(result);
        assertEquals(subscriptionClient, result);
    }
}
