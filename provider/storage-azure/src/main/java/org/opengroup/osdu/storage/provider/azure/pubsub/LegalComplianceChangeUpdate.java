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
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microsoft.azure.servicebus.IMessage;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceMessagePushReceiver;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.azure.config.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeContextHolder;
import org.opengroup.osdu.storage.provider.azure.model.LegalTagsChangedRequest;
import org.opengroup.osdu.storage.provider.azure.model.LegalTagsChangedData;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component
public class LegalComplianceChangeUpdate{
    private final static Logger LOGGER = LoggerFactory.getLogger(LegalComplianceChangeUpdate.class);

    @Autowired
    private IRecordsMetadataRepository recordsRepo;
    @Autowired
    private StorageAuditLogger auditLogger;
//    @Autowired
//   private ThreadDpsHeaders headers; //to be used when azure.feature.legaltag-compliance-update.enabled is set
    @Autowired
   private DpsHeaders headers;
    @Autowired
    private MDCContextMap mdcContextMap;
    @Autowired
    private ComplianceMessagePullReceiver complianceMessagePullReceiver;

    public void updateCompliance(IMessage message) throws ComplianceUpdateStoppedException , Exception{
        Gson gson = new Gson();
        try {
            String messageBody = new String(message.getMessageBody().getBinaryData().get(0), UTF_8);
            JsonElement jsonRoot = JsonParser.parseString(messageBody);
            LegalTagsChangedRequest legalTagsChangedRequest = gson.fromJson(jsonRoot, LegalTagsChangedRequest.class);
            LegalTagsChangedData legalTagsChangedData = gson.fromJson(legalTagsChangedRequest.getData(), LegalTagsChangedData.class);
            LegalTagChangedCollection tags = gson.fromJson(legalTagsChangedData.getData(), LegalTagChangedCollection.class);

            message.setMessageId(legalTagsChangedRequest.getId());
            //uncomment when azure.feature.legaltag-compliance-update.enabled is enabled
            //headers.setThreadContext(legalTagsChangedData.getDataPartitionId(), legalTagsChangedData.getCorrelationId(), legalTagsChangedData.getUser());
            MDC.setContextMap(mdcContextMap.getContextMap(headers.getCorrelationId(), headers.getCorrelationId()));

            complianceMessagePullReceiver.receiveMessage(tags, headers);
        }catch (NullPointerException ex){
            LOGGER.error("Invalid format for message with id: {}", message.getMessageId(), ex);
            throw new NullPointerException(ex.toString());
        }
        catch (Exception ex){
            LOGGER.error(String.format("Error occurred while updating compliance on records: %s", ex.getMessage()), ex);
            throw new Exception(ex.toString());
        }
        finally {
            ThreadScopeContextHolder.getContext().clear();
            MDC.clear();
        }
    }
}
