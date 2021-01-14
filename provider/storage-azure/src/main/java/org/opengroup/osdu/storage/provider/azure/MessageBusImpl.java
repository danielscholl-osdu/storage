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
import com.google.gson.JsonObject;
import com.microsoft.azure.eventgrid.models.EventGridEvent;
import com.microsoft.azure.servicebus.Message;
import org.joda.time.DateTime;
import org.opengroup.osdu.azure.eventgrid.EventGridTopicStore;
import org.opengroup.osdu.azure.eventgrid.TopicName;
import org.opengroup.osdu.azure.servicebus.ITopicClientFactory;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.azure.di.EventGridConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Named;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class MessageBusImpl implements IMessageBus {
    @Autowired
    private ITopicClientFactory topicClientFactory;

    @Autowired
    private EventGridTopicStore eventGridTopicStore;

    @Autowired
    private JaxRsDpsLog logger;

    @Autowired
    @Named("SERVICE_BUS_TOPIC")
    private String serviceBusTopic;

    @Autowired
    EventGridConfig eventGridConfig;

    private final static String EVENT_SUBJECT = "RecordsChanged";
    private final static String EVENT_TYPE = "RecordsChanged";
    private final static String EVENT_DATA_VERSION = "1.0";

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
        publishToServiceBus(headers, messages);
        if(eventGridConfig.isPublishingToEventGridEnabled()){
            publishToEventGrid(headers, messages);
        }
    }

    private void publishToEventGrid(DpsHeaders headers, PubSubInfo[] messages) {
        final Integer BATCH_SIZE = eventGridConfig.getEventGridBatchSize();
        List<EventGridEvent> eventsList = new ArrayList<>();

        for (int i = 0; i < messages.length; i += BATCH_SIZE) {

            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

            HashMap<String, Object> data = new HashMap<>();
            data.put("data", batch);
            data.put(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
            data.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
            data.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());

            String messageId = UUID.randomUUID().toString();
            eventsList.add(new EventGridEvent(
                    messageId,
                    EVENT_SUBJECT,
                    data,
                    EVENT_TYPE,
                    DateTime.now(),
                    EVENT_DATA_VERSION
            ));
            logger.info("Event generated: " + messageId);
        }

        // If a record change is not published (publishToEventGridTopic throws) we fail the job.
        // This is done to make sure no notifications are missed.
        eventGridTopicStore.publishToEventGridTopic(headers.getPartitionId(), TopicName.RECORDS_CHANGED, eventsList);
    }


    private void publishToServiceBus(DpsHeaders headers, PubSubInfo[] messages) {
        final int BATCH_SIZE = 50;
        Gson gson = new Gson();

        for (int i = 0; i < messages.length; i += BATCH_SIZE) {
            Message message = new Message();
            Map<String, Object> properties = new HashMap<>();

            // properties
            properties.put(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
            properties.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
            headers.addCorrelationIdIfMissing();
            properties.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
            message.setProperties(properties);

            // data
            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

            // add all to body {"message": {"data":[], "id":...}}
            JsonObject jo = new JsonObject();
            jo.add("data", gson.toJsonTree(batch));
            jo.addProperty(DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
            jo.addProperty(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
            jo.addProperty(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
            JsonObject jomsg = new JsonObject();
            jomsg.add("message", jo);

            message.setBody(jomsg.toString().getBytes(StandardCharsets.UTF_8));
            message.setContentType("application/json");

            try {
                logger.info("Storage publishes message to Service Bus " + headers.getCorrelationId());
                topicClientFactory.getClient(headers.getPartitionId(), serviceBusTopic).send(message);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

}