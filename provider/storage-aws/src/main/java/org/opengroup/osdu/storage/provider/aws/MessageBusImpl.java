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
import com.google.gson.Gson;
import com.amazonaws.services.sns.AmazonSNS;
import org.opengroup.osdu.core.aws.ssm.ParameterStorePropertySource;
import org.opengroup.osdu.core.aws.ssm.SSMConfig;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.aws.sns.AmazonSNSConfig;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Component
public class MessageBusImpl implements IMessageBus {

    private String amazonSNSTopic;

    @Value("${aws.region}")
    private String amazonSNSRegion;

    @Value("${aws.sns.topic.arn}")
    private String parameter;

    private AmazonSNS snsClient;

    @Inject
    private JaxRsDpsLog logger;

    private ParameterStorePropertySource ssm;

    @PostConstruct
    public void init(){
        AmazonSNSConfig config = new AmazonSNSConfig(amazonSNSRegion);
        snsClient = config.AmazonSNS();
        SSMConfig ssmConfig = new SSMConfig();
        ssm = ssmConfig.amazonSSM();
        amazonSNSTopic = ssm.getProperty(parameter).toString();
    }

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
        final int BATCH_SIZE = 50;
        Gson gson = new Gson();
        for (int i =0; i < messages.length; i+= BATCH_SIZE){

            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));
            String json = gson.toJson(batch);

            // Attributes
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
            PublishRequest publishRequest = new PublishRequest(amazonSNSTopic, json)
                    .withMessageAttributes(messageAttributes);

            logger.info("Storage publishes message " + headers.getCorrelationId());
            snsClient.publish(publishRequest);

        }
    }
}
