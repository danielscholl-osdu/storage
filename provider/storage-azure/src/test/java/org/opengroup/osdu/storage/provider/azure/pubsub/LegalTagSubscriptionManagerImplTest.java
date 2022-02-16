package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class LegalTagSubscriptionManagerImplTest {
    private static final String maxLockRenewDuration = "60";
    private static final String maxConcurrentCalls = "1";
    private static final String nThreads = "2";
    private static final String errorMessage = "some-error";

    @InjectMocks
    private LegalTagSubscriptionManagerImpl subscriptionManager;

    @Mock
    private LegalTagSubscriptionClientFactory subscriptionClientFactory;

    @Mock
    private AzureBootstrapConfig azureBootstrapConfig;

    @Mock
    private ServiceBusConfig serviceBusConfig;

    @Mock
    private SubscriptionClient subscriptionClient;

    @Mock
    private ITenantFactory tenantFactory;

    private static final String dataPartition = "testTenant";

    @BeforeEach
    public void init() {
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setDataPartitionId(dataPartition);

        when(serviceBusConfig.getMaxConcurrentCalls()).thenReturn(maxConcurrentCalls);
        when(serviceBusConfig.getSbExecutorThreadPoolSize()).thenReturn(nThreads);
        when(serviceBusConfig.getMaxLockRenewDurationInSeconds()).thenReturn(maxLockRenewDuration);
        when(tenantFactory.listTenantInfo()).thenReturn(Collections.singletonList(tenantInfo));
    }

    @Test
    public void shouldSuccessfullyRegisterMessageHandler() throws ServiceBusException, InterruptedException {

        doNothing().when(subscriptionClient).registerMessageHandler(any(), any(), any());
        when(subscriptionClientFactory.getSubscriptionClient(dataPartition, serviceBusConfig.getLegalServiceBusTopic(), serviceBusConfig.getLegalServiceBusTopicSubscription())).thenReturn(subscriptionClient);

        subscriptionManager.subscribeLegalTagsChangeEvent();

        verify(serviceBusConfig, times(1)).getMaxConcurrentCalls();
        verify(serviceBusConfig, times(1)).getSbExecutorThreadPoolSize();
        verify(serviceBusConfig, times(1)).getMaxLockRenewDurationInSeconds();
    }



    @Test
    public void shouldThrowExceptionIfErrorWhileRegisteringMessageHandler() throws ServiceBusException, InterruptedException {

        doThrow(new InterruptedException(errorMessage)).when(subscriptionClient).registerMessageHandler(any(), any(), any());
        when(subscriptionClientFactory.getSubscriptionClient(dataPartition, serviceBusConfig.getLegalServiceBusTopic(), serviceBusConfig.getLegalServiceBusTopicSubscription())).thenReturn(subscriptionClient);

        subscriptionManager.subscribeLegalTagsChangeEvent();

        verify(serviceBusConfig, times(1)).getMaxConcurrentCalls();
        verify(serviceBusConfig, times(1)).getSbExecutorThreadPoolSize();
        verify(serviceBusConfig, times(1)).getMaxLockRenewDurationInSeconds();
    }




}
