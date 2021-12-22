/*
  Copyright 2020 Google LLC
  Copyright 2020 EPAM Systems, Inc

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.pubsub;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.gcp.oqm.driver.OqmDriver;
import org.opengroup.osdu.core.gcp.oqm.model.OqmDestination;
import org.opengroup.osdu.core.gcp.oqm.model.OqmMessage;
import org.opengroup.osdu.core.gcp.oqm.model.OqmTopic;
import org.opengroup.osdu.storage.provider.gcp.util.GcpAppServiceConfig;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class OqmPubSub implements IMessageBus {

    private final GcpAppServiceConfig config;
    private final OqmDriver driver;
    private final TenantInfo tenant;
    private final int BATCH_SIZE = 50;

    private OqmTopic oqmTopic = null;

    @PostConstruct
    void postConstruct() {
        oqmTopic = OqmTopic.builder().name(config.getPubsubSearchTopic()).build();
    }

    @Override
    public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {

        OqmDestination oqmDestination = OqmDestination.builder().partitionId(headers.getPartitionId()).build();

        for (int i = 0; i < messages.length; i += BATCH_SIZE) {

            PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

            String json = new Gson().toJson(batch);

            Map<String, String> attributes = new HashMap<>();
            attributes.put(DpsHeaders.ACCOUNT_ID, this.tenant.getName());
            attributes.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
            headers.addCorrelationIdIfMissing();
            attributes.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());

            OqmMessage oqmMessage = OqmMessage.builder().data(json).attributes(attributes).build();

            driver.publish(oqmMessage, oqmTopic, oqmDestination);

        }
    }
}