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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.aws.util.CollaborationContextTestUtil;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


class MessageBusImplTest {

    private final static String AWS_SNS_TOPIC = "storage_sqs_url";
    private final static String AWS_SNS_TOPIC_V2 = "storage_sqs_url_v2";

    @InjectMocks
    private MessageBusImpl messageBus;

    @Mock
    private AmazonSNS snsClient;

    @Mock
    private JaxRsDpsLog logger;

    @Captor
    private ArgumentCaptor<PublishRequest> publishRequestCaptor;


    @BeforeEach
    void setUp() throws K8sParameterNotFoundException {
        openMocks(this);
    }

    private void initAndRun(Runnable runnable) throws K8sParameterNotFoundException {
        try (MockedConstruction<K8sLocalParameterProvider> provider = Mockito.mockConstruction(K8sLocalParameterProvider.class, (mock, context) -> {
            when(mock.getParameterAsString("storage-sns-topic-arn")).thenReturn(AWS_SNS_TOPIC);
            when(mock.getParameterAsString("storage-v2-sns-topic-arn")).thenReturn(AWS_SNS_TOPIC_V2);
        })) {
            try (MockedConstruction<AmazonSNSConfig> config = Mockito.mockConstruction(AmazonSNSConfig.class, (mock1, context) -> {
                when(mock1.AmazonSNS()).thenReturn(snsClient);
            })) {
                messageBus.init();
                runnable.run();
            }
        }
    }

    private void assertMessageHasAttributes(PublishRequest message, String key, String value) {
        Map<String, MessageAttributeValue> attrMap = message.getMessageAttributes();
        MessageAttributeValue messageAttr = attrMap.get(key);
        assertNotEquals(null, messageAttr);
        assertEquals("String", messageAttr.getDataType());
        assertEquals(value, messageAttr.getStringValue());
    }

    private void assertMessageDoesNotHaveAttributes(PublishRequest message, String key) {
        Map<String, MessageAttributeValue> attrMap = message.getMessageAttributes();
        MessageAttributeValue messageAttr = attrMap.get(key);
        assertEquals(null, messageAttr);
    }

    @Test
    void publishMessageOutsideForRecordNotOnNamespace() throws K8sParameterNotFoundException {
        initAndRun(() -> {
                    // arrange
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

                    assertEquals(AWS_SNS_TOPIC, receivedRequest.getTopicArn());
                    assertMessageHasAttributes(receivedRequest, DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
                    assertMessageHasAttributes(receivedRequest, DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
                    assertMessageHasAttributes(receivedRequest, DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
                    assertMessageHasAttributes(receivedRequest, DpsHeaders.USER_EMAIL, headers.getUserEmail());
                    assertMessageHasAttributes(receivedRequest, DpsHeaders.AUTHORIZATION, headers.getAuthorization());
                    assertMessageDoesNotHaveAttributes(receivedRequest, DpsHeaders.COLLABORATION);

                }
        );
    }

    @Test
    void publishMessageForRecordOnNamespace() throws K8sParameterNotFoundException {
        initAndRun(() -> {
            // arrange
            DpsHeaders headers = new DpsHeaders();
            RecordChangedV2 message = new RecordChangedV2();
            message.setKind("common:welldb:wellbore:1.0.12311");
            message.setOp(OperationType.create_schema);

            RecordChangedV2[] messages = new RecordChangedV2[1];
            messages[0] = message;
            Mockito.when(snsClient.publish(publishRequestCaptor.capture()))
                    .thenReturn(any(PublishResult.class));
            // act
            final CollaborationContext collaborationContext = CollaborationContextTestUtil.getACollaborationContext();
            final String collaborationContextString = String.format("id=%s,application=%s", collaborationContext.getId(), collaborationContext.getApplication());

            messageBus.publishMessage(Optional.of(collaborationContext), headers, messages);

            // assert
            Mockito.verify(snsClient, Mockito.times(1)).publish(any(PublishRequest.class));

            PublishRequest receivedRequest = publishRequestCaptor.getValue();

            assertEquals(AWS_SNS_TOPIC_V2, receivedRequest.getTopicArn());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.ACCOUNT_ID, headers.getPartitionIdWithFallbackToAccountId());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.USER_EMAIL, headers.getUserEmail());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.AUTHORIZATION, headers.getAuthorization());
            assertMessageHasAttributes(receivedRequest, DpsHeaders.COLLABORATION, collaborationContextString);
        });
    }
}
