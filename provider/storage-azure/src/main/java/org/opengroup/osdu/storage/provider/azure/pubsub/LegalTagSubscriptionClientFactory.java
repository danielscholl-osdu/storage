package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.SubscriptionClient;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.opengroup.osdu.azure.servicebus.ISubscriptionClientFactory;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LegalTagSubscriptionClientFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionClientFactory.class);
    @Autowired
    AzureBootstrapConfig azureBootstrapConfig;
    @Autowired
    private ISubscriptionClientFactory subscriptionClientFactory;

    public SubscriptionClient getSubscriptionClient(String dataPartition, String serviceBusTopic, String serviceBusTopicSubscription) {
        try {
            return subscriptionClientFactory.getClient(dataPartition, serviceBusTopic, serviceBusTopicSubscription);
        } catch (ServiceBusException | InterruptedException e) {
            LOGGER.error("Unexpected error creating Subscription Client", e);
            throw new AppException(500, "Server Error", "Unexpected error creating Subscription Client", e);
        }
    }
}
