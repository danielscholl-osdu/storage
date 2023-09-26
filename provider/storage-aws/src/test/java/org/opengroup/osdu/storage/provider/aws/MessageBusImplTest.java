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

package org.opengroup.osdu.storage.provider.aws;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.MockitoAnnotations.openMocks;


class MessageBusImplTest {

    @InjectMocks
    private MessageBusImpl messageBus;

    @Mock
    private AmazonSNS snsClient;

    @Mock
    private JaxRsDpsLog logger;

    @Captor
    private ArgumentCaptor<PublishRequest> publishRequestCaptor;

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    private void assertMessageHasAttributes(PublishRequest message, String key, String value) {
        Map<String, MessageAttributeValue> attrMap = message.getMessageAttributes();
        MessageAttributeValue messageAttr = attrMap.get(key);
        assertNotEquals(null, messageAttr);
        assertEquals("String", messageAttr.getDataType());
        assertEquals(value, messageAttr.getStringValue());
    }

    @Test
    void publishMessage() {
        // arrange
        String amazonSNSTopic = null;
        DpsHeaders headers = new DpsHeaders();
        PubSubInfo message = new PubSubInfo();
        message.setKind("common:welldb:wellbore:1.0.12311");
        message.setOp(OperationType.create_schema);

        PubSubInfo[] messages = new PubSubInfo[1];
        messages[0] = message;
        Mockito.when(snsClient.publish(publishRequestCaptor.capture()))
                .thenReturn(any(PublishResult.class));

        // act
        messageBus.publishMessage(headers, messages);

        // assert
        Mockito.verify(snsClient, Mockito.times(1)).publish(any(PublishRequest.class));

        PublishRequest receivedRequest = publishRequestCaptor.getValue();

        assertMessageHasAttributes(receivedRequest, DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
        assertMessageHasAttributes(receivedRequest, DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
        assertMessageHasAttributes(receivedRequest, DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        assertMessageHasAttributes(receivedRequest, DpsHeaders.USER_EMAIL, headers.getUserEmail());
        assertMessageHasAttributes(receivedRequest, DpsHeaders.AUTHORIZATION, headers.getAuthorization());
    }
}
