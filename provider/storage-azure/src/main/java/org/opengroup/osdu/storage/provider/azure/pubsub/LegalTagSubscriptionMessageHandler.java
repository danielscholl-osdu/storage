// Copyright © Microsoft Corporation
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.microsoft.azure.servicebus.ExceptionPhase;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.IMessageHandler;
import com.microsoft.azure.servicebus.SubscriptionClient;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CompletableFuture;

public class LegalTagSubscriptionMessageHandler implements IMessageHandler {
    @Autowired
    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagSubscriptionMessageHandler.class);
    private final SubscriptionClient receiveClient;
    private final LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    public LegalTagSubscriptionMessageHandler(SubscriptionClient client, LegalComplianceChangeUpdate legalComplianceChangeServiceAzure) {
        this.receiveClient = client;
        this.legalComplianceChangeUpdate = legalComplianceChangeServiceAzure;
    }

    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        try {
            this.legalComplianceChangeUpdate.updateCompliance(message);
            return this.receiveClient.completeAsync(message.getLockToken());
        } catch (ComplianceUpdateStoppedException e) {
            LOGGER.error("Exception while processing legal tag subscription.", e);
            return this.receiveClient.abandonAsync(message.getLockToken());
        }
        catch (Exception e) {
            LOGGER.error("Exception while processing legal tag subscription.", e);
            return this.receiveClient.abandonAsync(message.getLockToken());
        }
    }

    @Override
    public void notifyException(Throwable throwable, ExceptionPhase exceptionPhase) {
        LOGGER.error("{} - {}", exceptionPhase, throwable.getMessage());
    }


}
