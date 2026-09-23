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

import com.microsoft.azure.servicebus.IMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(value = "azure.feature.legaltag-compliance-update.enabled", havingValue = "true", matchIfMissing = false)
@Component
public class LegalTagMessageHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(LegalTagMessageHandler.class);

    @Autowired
    private LegalComplianceChangeUpdate legalComplianceChangeUpdate;

    public void handle(IMessage message) throws Exception {
        LOGGER.info("Processing LegalTag message with delivery count: {}", message.getDeliveryCount());
        legalComplianceChangeUpdate.updateCompliance(message);
    }

    public void handleFailure(IMessage message) {
        LOGGER.error("Processing failure for LegalTag message");
    }
}
