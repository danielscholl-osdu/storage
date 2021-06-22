package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.SubscriptionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

public class LegalTagSubscriptionMessageHandler implements IMessageHandler {
    @Autowired
    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionMessageHandler.class);
    private final SubscriptionClient receiveClient;
    private final LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure;

    public LegalTagSubscriptionMessageHandler(SubscriptionClient client, LegalComplianceChangeServiceAzureImpl legalComplianceChangeServiceAzure) {
        this.receiveClient = client;
        this.legalComplianceChangeServiceAzure = legalComplianceChangeServiceAzure;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        try {
            this.legalComplianceChangeServiceAzure.updateCompliance(message);
        } catch (Exception e) {
            LOGGER.error("Exception while processing legal tag subscription.", e);
        }
        return this.receiveClient.completeAsync(message.getLockToken());
    }

    @Override
    public void notifyException(Throwable throwable, ExceptionPhase exceptionPhase) {
        LOGGER.error("{} - {}", exceptionPhase, throwable.getMessage());
    }


}
