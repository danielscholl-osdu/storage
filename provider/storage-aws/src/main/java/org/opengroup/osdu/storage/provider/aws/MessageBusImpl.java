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

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import org.apache.commons.lang3.NotImplementedException;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import com.amazonaws.services.sns.AmazonSNS;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;
import org.opengroup.osdu.core.aws.sns.PublishRequestBuilder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class MessageBusImpl implements IMessageBus {

    private String amazonSNSTopic;
    private String amazonSNSTopicV2;
    private AmazonSNS snsClient;
    @Value("${AWS.REGION}")
    private String currentRegion;

    @Value("${OSDU_TOPIC}")
    private String osduStorageTopic;

    @Value("${OSDU_TOPIC_V2}")
    private String osduStorageTopicV2;

    @Inject
    private JaxRsDpsLog logger;

    private <T> void doPublishMessage(boolean v2Message, Optional<CollaborationContext> collaborationContext, DpsHeaders headers, T... messages) {
        final int BATCH_SIZE = 50;
        PublishRequestBuilder<T> publishRequestBuilder = new PublishRequestBuilder<>();
        publishRequestBuilder.setGeneralParametersFromHeaders(headers);
        logger.info("Storage publishes message " + headers.getCorrelationId());
        for (int i =0; i < messages.length; i+= BATCH_SIZE){

            T[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

            Map<String, MessageAttributeValue> additionalAttrs = new HashMap<>();
            String messageSNSTopic;
            String messageOsduTopic;

            if (v2Message) {
                if (collaborationContext.isPresent()) {
                    additionalAttrs.put(DpsHeaders.COLLABORATION, getAttrValForContext(collaborationContext.get()));
                }
                messageOsduTopic = osduStorageTopicV2; //records-changed-v2
                messageSNSTopic = amazonSNSTopicV2;
            } else {
                messageOsduTopic = osduStorageTopic;
                messageSNSTopic = amazonSNSTopic;
            }
            PublishRequest publishRequest = publishRequestBuilder.generatePublishRequest(messageOsduTopic, messageSNSTopic, Arrays.asList(batch), additionalAttrs);

            snsClient.publish(publishRequest);
        }
    }

    private static MessageAttributeValue getAttrValForContext(CollaborationContext collaborationContext) {
        return new MessageAttributeValue().withDataType("String").withStringValue("id=" + collaborationContext.getId() + ",application=" + collaborationContext.getApplication());
    }

    @PostConstruct
    public void init() throws K8sParameterNotFoundException {
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        amazonSNSTopic = provider.getParameterAsString("storage-sns-topic-arn");
        amazonSNSTopicV2 = provider.getParameterAsString("storage-v2-sns-topic-arn");
        snsClient = new AmazonSNSConfig(currentRegion).AmazonSNS();
    }

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
        doPublishMessage(false,Optional.empty(), headers, messages);
    }

    @Override
    public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {
        doPublishMessage(true, collaborationContext, headers, messages);
    }

    @Override
    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
        throw new NotImplementedException();
    }

    @Override
    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, PubSubInfo... messages) {
        throw new NotImplementedException();
    }
}
