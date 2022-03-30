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

import com.microsoft.azure.servicebus.primitives.ServiceBusException;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.publisherFacade.MessagePublisher;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.azure.di.EventGridConfig;
import org.opengroup.osdu.storage.provider.azure.di.ServiceBusConfig;
import org.opengroup.osdu.storage.provider.azure.di.PublisherConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@ExtendWith(MockitoExtension.class)
public class MessageBusImplTest {
    private static final String TOPIC_NAME = "recordstopic";
    private final static String RECORDS_CHANGED_EVENT_SUBJECT = "RecordsChanged";
    private final static String RECORDS_CHANGED_EVENT_TYPE = "RecordsChanged";
    private final static String RECORDS_CHANGED_EVENT_DATA_VERSION = "1.0";
    @Mock
    private MessagePublisher messagePublisher;
    @Mock
    private ServiceBusConfig serviceBusConfig;
    @Mock
    private EventGridConfig eventGridConfig;
    @Mock
    private PublisherConfig publisherConfig;
    @Mock
    private DpsHeaders dpsHeaders;
    @InjectMocks
    private MessageBusImpl sut;

    @Before
    public void init() throws ServiceBusException, InterruptedException {
        initMocks(this);
        doReturn("10").when(publisherConfig).getPubSubBatchSize();
        doReturn(TOPIC_NAME).when(eventGridConfig).getEventGridTopic();
        doReturn(TOPIC_NAME).when(serviceBusConfig).getServiceBusTopic();
        doReturn(RECORDS_CHANGED_EVENT_SUBJECT).when(eventGridConfig).getEventSubject();
        doReturn(RECORDS_CHANGED_EVENT_DATA_VERSION).when(eventGridConfig).getEventDataVersion();
        doReturn(RECORDS_CHANGED_EVENT_TYPE).when(eventGridConfig).getEventType();
    }

    @Test
    public void should_publishToMessagePublisher() {
        // Set Up
        String[] ids = {"id1", "id2", "id3", "id4", "id5", "id6", "id7", "id8", "id9", "id10", "id11"};
        String[] kinds = {"kind1", "kind2", "kind3", "kind4", "kind5", "kind6", "kind7", "kind8", "kind9", "kind10", "kind11"};
        doNothing().when(messagePublisher).publishMessage(eq(dpsHeaders), any());
        doReturn("id").when(dpsHeaders).getCorrelationId();

        PubSubInfo[] pubSubInfo = new PubSubInfo[11];
        for (int i = 0; i < ids.length; ++i) {
            pubSubInfo[i] = getPubsInfo(ids[i], kinds[i]);
        }
        sut.publishMessage(dpsHeaders, pubSubInfo);
    }

    private PubSubInfo getPubsInfo(String id, String kind) {
        PubSubInfo pubSubInfo = new PubSubInfo();
        pubSubInfo.setId(id);
        pubSubInfo.setKind(kind);
        return pubSubInfo;
    }
}
