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

import com.google.gson.Gson;
import com.microsoft.azure.servicebus.ITopicClient;
import com.microsoft.azure.servicebus.Message;
import com.microsoft.azure.servicebus.MessageBody;
import org.opengroup.osdu.azure.publisherFacade.MessagePublisher;
import org.opengroup.osdu.azure.publisherFacade.PublisherInfo;
import org.opengroup.osdu.azure.servicebus.ITopicClientFactory;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.azure.di.EventGridConfig;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.di.PublisherConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@Component
public class MessageBusImpl implements IMessageBus {

    private final static Logger LOGGER = LoggerFactory.getLogger(MessageBusImpl.class);

    @Autowired
    ServiceBusConfig serviceBusConfig;

    @Autowired
    private EventGridConfig eventGridConfig;

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private PublisherConfig publisherConfig;

    @Autowired
    private ITopicClientFactory topicClientFactory;

    private final static Logger logger = LoggerFactory.getLogger(MessageBusImpl.class);

    private final static String ROUTING_KEY = "topic";

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {

        // The batch size is same for both Event grid and Service bus.
        final int BATCH_SIZE = Integer.parseInt(publisherConfig.getPubSubBatchSize());
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            logger.info(String.format("The correlation id for this is %s",headers.getCorrelationId()));
            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            PublisherInfo publisherInfo = getPartialPublisherInfo();
            publisherInfo.setBatch(batch);
            publisherInfo.setServiceBusTopicName(serviceBusConfig.getServiceBusTopic());
            messagePublisher.publishMessage(headers, publisherInfo, Optional.empty());
        }
    }

    @Override
    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, PubSubInfo... messages) {

        int messageSequenceTracker = 0;
        // The batch size is same for both Event grid and Service bus.
        final int BATCH_SIZE = Integer.parseInt(routingInfo.get("publisherBatchSize"));
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            DpsHeaders dpsHeaders = this.addMessageTracingToDpsHeaders(headers,messageSequenceTracker);
            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            PublisherInfo publisherInfo = getPartialPublisherInfo();
            publisherInfo.setBatch(batch);
            publisherInfo.setServiceBusTopicName(routingInfo.get(ROUTING_KEY));
            publisherInfo.setMessageId(dpsHeaders.getCorrelationId());
            logger.info("Publishing messages with correlation ID: {} to the topic: {}",dpsHeaders.getCorrelationId(),routingInfo.get(ROUTING_KEY));
            messagePublisher.publishMessage(dpsHeaders, publisherInfo, Optional.empty());
            messageSequenceTracker++;
        }
    }

    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {

        List<Message> messageBatch = new ArrayList<>();
        for (Object messageObject : messageList) {
            Message message = new Message();
            String json = new Gson().toJson(messageObject);
            message.setMessageBody(MessageBody.fromValueData(json));
            message.setCorrelationId(headers.getCorrelationId());
            message.setContentType("application/json");
            messageBatch.add(message);
        }
        ITopicClient client;
        try {
            client = topicClientFactory.getClient(headers.getPartitionId(), routingInfo.get(ROUTING_KEY));
            client.sendBatch(messageBatch);
        } catch (Exception e) {
            logger.info( "The following exception occurred during the publish message event {}",
                    e.getMessage());
            throw new RuntimeException("Some exception occurred during the publish message event.", e);
        }
    }

    @Override
    public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {

        // The batch size is same for both Event grid and Service bus.
        final int BATCH_SIZE = Integer.parseInt(publisherConfig.getPubSubBatchSize());
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            String messageId = String.format("%s-%d", headers.getCorrelationId(), i);
            RecordChangedV2[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            PublisherInfo publisherInfo = getPartialPublisherInfo();
            publisherInfo.setBatch(batch);
            publisherInfo.setMessageId(messageId);
            publisherInfo.setServiceBusTopicName(serviceBusConfig.getServiceBusRecordsEventTopic());
            messagePublisher.publishMessage(headers, publisherInfo, collaborationContext);
        }
    }

    private PublisherInfo getPartialPublisherInfo() {
        return PublisherInfo.builder()
                .eventGridTopicName(eventGridConfig.getEventGridTopic())
                .eventGridEventSubject(eventGridConfig.getEventSubject())
                .eventGridEventType(eventGridConfig.getEventType())
                .eventGridEventDataVersion(eventGridConfig.getEventDataVersion())
                .build();
    }

    private DpsHeaders addMessageTracingToDpsHeaders(DpsHeaders headers, int messageSequenceTracker) {
        String correlationId = headers.getCorrelationId() + "_" + messageSequenceTracker;
        DpsHeaders dpsHeaders = new DpsHeaders();
        dpsHeaders.put(DpsHeaders.CORRELATION_ID,correlationId);
        dpsHeaders.put(DpsHeaders.DATA_PARTITION_ID,headers.getPartitionId());
        return dpsHeaders;
    }
}
