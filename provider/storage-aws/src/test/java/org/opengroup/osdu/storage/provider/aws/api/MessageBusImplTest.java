// Copyright © 2020 Amazon Web Services
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

package org.opengroup.osdu.storage.provider.aws.api;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.StorageApplication;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.aws.MessageBusImpl;

import org.springframework.boot.test.context.SpringBootTest;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = {StorageApplication.class})
public class MessageBusImplTest {

    @InjectMocks
    private MessageBusImpl messageBus = new MessageBusImpl();

    @Mock
    private AmazonSNS snsClient;

    @Mock
    private JaxRsDpsLog logger;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void publishMessage() {
        // arrange
        String amazonSNSTopic = null;
        DpsHeaders headers = new DpsHeaders();
        PubSubInfo message = new PubSubInfo();
        message.setKind("common:welldb:wellbore:1.0.12311");
        message.setOp(OperationType.create_schema);

        PubSubInfo[] messages = new PubSubInfo[1];
        messages[0] = message;
        Mockito.when(snsClient.publish(Mockito.any(PublishRequest.class)))
                .thenReturn(Mockito.any(PublishResult.class));

        final int BATCH_SIZE = 50;
        Gson gson = new Gson();
        PublishRequest publishRequest = new PublishRequest();
        for (int i =0; i < messages.length; i+= BATCH_SIZE) {

            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            String json = gson.toJson(batch);

            // attributes
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put(DpsHeaders.ACCOUNT_ID, new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(headers.getPartitionIdWithFallbackToAccountId()));
            messageAttributes.put(DpsHeaders.DATA_PARTITION_ID, new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(headers.getPartitionIdWithFallbackToAccountId()));
            headers.addCorrelationIdIfMissing();
            messageAttributes.put(DpsHeaders.CORRELATION_ID, new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(headers.getCorrelationId()));
            messageAttributes.put(DpsHeaders.USER_EMAIL, new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(headers.getUserEmail()));
            messageAttributes.put(DpsHeaders.AUTHORIZATION, new MessageAttributeValue()
                    .withDataType("String")
                    .withStringValue(headers.getAuthorization()));
            publishRequest.setMessage(json);
            publishRequest.setMessageAttributes(messageAttributes);
        }

        // act
        messageBus.publishMessage(headers, message);

        // assert
        Mockito.verify(snsClient, Mockito.times(1)).publish(Mockito.eq(publishRequest));
    }
}
