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

import com.microsoft.azure.servicebus.*;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.CompletableFuture;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public class ReplaySubscriptionMessageHandler implements IMessageHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(ReplaySubscriptionMessageHandler.class);

    private final SubscriptionClient receiveClient;

    private final int MAX_DELIVERY_COUNT = 3;

    private ReplayMessageHandler replayMessageHandler;

    public ReplaySubscriptionMessageHandler(SubscriptionClient subscriptionClient, ReplayMessageHandler replayMessageHandler) {
        this.receiveClient = subscriptionClient;
        this.replayMessageHandler = replayMessageHandler;
    }

    @SneakyThrows
    @Override
    public CompletableFuture<Void> onMessageAsync(IMessage message) {
        try {
            replayMessageHandler.handle(message);
            return this.receiveClient.completeAsync(message.getLockToken());
        } catch (Exception e) {
            LOGGER.error("Exception while processing replay topic message.", e);
            if (message.getDeliveryCount() >= MAX_DELIVERY_COUNT) {
                LOGGER.error("Max Delivery Count of {} Exceeded for Message; Dead Lettering the message.", MAX_DELIVERY_COUNT, e);
                replayMessageHandler.handleFailure(message);
                return this.receiveClient.deadLetterAsync(message.getLockToken());
            }
            LOGGER.warn("Attempt {}/{} failed.", message.getDeliveryCount(), MAX_DELIVERY_COUNT, e);
            return this.receiveClient.abandonAsync(message.getLockToken());
        }
    }

    @Override
    public void notifyException(Throwable throwable, ExceptionPhase exceptionPhase) {
        
        LOGGER.error("{} - {}", exceptionPhase, throwable.getMessage());
    }
}
