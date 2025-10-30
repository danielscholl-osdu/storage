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

package org.opengroup.osdu.storage.provider.azure.pubsub;

import com.google.gson.Gson;
import com.microsoft.azure.servicebus.IMessage;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.azure.config.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.opengroup.osdu.storage.service.replay.IReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static java.nio.charset.StandardCharsets.UTF_8;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
@Component
public class ReplayMessageHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReplayMessageHandler.class);

    @Autowired
    private IReplayService replayService;

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private MDCContextMap mdcContextMap;

    public void handle(IMessage message) {

        ReplayMessage replayMessage = getReplayMessage(message);
        headers.put(DpsHeaders.DATA_PARTITION_ID,replayMessage.getDataPartitionId());
        headers.put(DpsHeaders.CORRELATION_ID,replayMessage.getCorrelationId());
        MDC.setContextMap(mdcContextMap.getContextMap(headers.getCorrelationId(), headers.getPartitionId()));
        LOGGER.info("Processing PerformReplayMessage with a delivery count of: {}", message.getDeliveryCount());
        replayService.processReplayMessage(replayMessage);
    }

    public void handleFailure(IMessage message) {

        LOGGER.info("Processing Failure for message");
        ReplayMessage replayMessage = getReplayMessage(message);
        replayService.processFailure(replayMessage);
        LOGGER.info("Processed Failure for ReplayMessage");
    }

    private ReplayMessage getReplayMessage(IMessage message) {

        String serviceBusMessageString = message.getMessageBody().getValueData().toString();
        return new Gson().fromJson(serviceBusMessageString, ReplayMessage.class);
    }
}
