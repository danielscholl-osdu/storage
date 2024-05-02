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
import java.util.Map;
import java.util.Optional;

@Component
public class MessageBusImpl implements IMessageBus {

    private String amazonSNSTopic;
    private AmazonSNS snsClient;
    @Value("${AWS.REGION}")
    private String currentRegion;

    @Value("${OSDU_TOPIC}")
    private String osduStorageTopic;

    @Inject
    private JaxRsDpsLog logger;

    @PostConstruct
    public void init() throws K8sParameterNotFoundException {
        K8sLocalParameterProvider provider = new K8sLocalParameterProvider();
        amazonSNSTopic = provider.getParameterAsString("storage-sns-topic-arn");
        snsClient = new AmazonSNSConfig(currentRegion).AmazonSNS();
    }

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
        final int BATCH_SIZE = 50;
        PublishRequestBuilder<PubSubInfo> publishRequestBuilder = new PublishRequestBuilder<>();
        publishRequestBuilder.setGeneralParametersFromHeaders(headers);
        logger.info("Storage publishes message " + headers.getCorrelationId());
        for (int i = 0; i < messages.length; i += BATCH_SIZE) {

            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

            PublishRequest publishRequest = publishRequestBuilder.generatePublishRequest(osduStorageTopic, amazonSNSTopic, Arrays.asList(batch));

            snsClient.publish(publishRequest);
        }
    }

    @Override
    public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {
        // To be implemented by aws provider
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
