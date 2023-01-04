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

package org.opengroup.osdu.storage.provider.azure;

import org.opengroup.osdu.azure.publisherFacade.MessagePublisher;
import org.opengroup.osdu.azure.publisherFacade.PublisherInfo;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.azure.di.EventGridConfig;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.di.PublisherConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.opengroup.osdu.storage.util.FeatureFlagUtil;

import java.util.*;

@Component
public class MessageBusImpl implements IMessageBus {
    @Autowired
    ServiceBusConfig serviceBusConfig;
    @Autowired
    private EventGridConfig eventGridConfig;
    @Autowired
    private MessagePublisher messagePublisher;
    @Autowired
    private PublisherConfig publisherConfig;
    @Autowired
    private FeatureFlagUtil featureFlagUtil;
    @Value("${collaboration.feature.flag.name:false}")
    private String collaborationFeatureFlagName;

    @Override
    public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, PubSubInfo... messages) {
        if (featureFlagUtil.isFeatureEnabled(collaborationFeatureFlagName, headers.getPartitionId())) {
            publishMessageToRecordsTopicV2(collaborationContext, headers, messages);
            if (collaborationContext.isPresent()) {
                return;
            }
        }
        // The batch size is same for both Event grid and Service bus.
        final int BATCH_SIZE = Integer.parseInt(publisherConfig.getPubSubBatchSize());
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            PublisherInfo publisherInfo = PublisherInfo.builder()
                    .batch(batch)
                    .eventGridTopicName(eventGridConfig.getEventGridTopic())
                    .eventGridEventSubject(eventGridConfig.getEventSubject())
                    .eventGridEventType(eventGridConfig.getEventType())
                    .eventGridEventDataVersion(eventGridConfig.getEventDataVersion())
                    .serviceBusTopicName(serviceBusConfig.getServiceBusTopic())
                    .build();

            messagePublisher.publishMessage(headers, publisherInfo, collaborationContext);
        }
    }


    public void publishMessageToRecordsTopicV2(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, PubSubInfo... messages) {
        // The batch size is same for both Event grid and Service bus.
        final int BATCH_SIZE = Integer.parseInt(publisherConfig.getPubSubBatchSize());
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            String messageId = String.format("%s-%d",headers.getCorrelationId(), i);
            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            PublisherInfo publisherInfo = PublisherInfo.builder()
                    .batch(batch)
                    .eventGridTopicName(eventGridConfig.getEventGridTopic())
                    .eventGridEventSubject(eventGridConfig.getEventSubject())
                    .eventGridEventType(eventGridConfig.getEventType())
                    .eventGridEventDataVersion(eventGridConfig.getEventDataVersion())
                    .serviceBusTopicName(serviceBusConfig.getServiceBusRecordsEventTopic())
                    .messageId(messageId)
                    .build();
            messagePublisher.publishMessage(headers, publisherInfo, collaborationContext);
        }
    }
}
