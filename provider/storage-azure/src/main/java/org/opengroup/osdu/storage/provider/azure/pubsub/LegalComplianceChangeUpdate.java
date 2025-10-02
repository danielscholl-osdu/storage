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

import com.azure.spring.cloud.core.service.AzureServiceType.ServiceBus;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.microsoft.azure.servicebus.IMessage;
import com.microsoft.azure.servicebus.primitives.ServiceBusException;

import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceUpdateStoppedException;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.storage.provider.azure.config.ThreadDpsHeaders;
import org.opengroup.osdu.storage.provider.azure.config.ThreadScopeContextHolder;
import org.opengroup.osdu.storage.provider.azure.model.LegalTagsChangedRequest;

import org.opengroup.osdu.storage.provider.azure.exception.ServiceBusInvalidMessageBodyException;
import org.opengroup.osdu.storage.provider.azure.util.MDCContextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(value = "azure.feature.legaltag-compliance-update.enabled", havingValue = "true", matchIfMissing = false)
public class LegalComplianceChangeUpdate {
    private final Gson gson = new Gson();
    private final Logger logger;

    public LegalComplianceChangeUpdate() {
        this(LoggerFactory.getLogger(LegalComplianceChangeUpdate.class));
    }

    public LegalComplianceChangeUpdate(Logger logger) {
        this.logger = logger;
    }

    @Autowired
    private ThreadDpsHeaders headers; //to be used when azure.feature.legaltag-compliance-update.enabled is set
    @Autowired
    private MDCContextMap mdcContextMap;
    @Autowired
    private ComplianceMessagePullReceiver complianceMessagePullReceiver;

    public void updateCompliance(IMessage message) throws ComplianceUpdateStoppedException , Exception {
        try {
            String messageBody = getMessageBodyAsString(message);
            logger.info(String.format("Received a message from the service bus with message ID %s", message.getMessageId()));
            JsonElement messageBodyAsJson = JsonParser.parseString(messageBody);

            LegalTagsChangedRequest legalTagsChangedRequest = getLegalTagsChangedRequest(messageBodyAsJson);
            LegalTagsChangedRequest.Message legalTagsChangedMessage = getLegalTagsChangedMessage(legalTagsChangedRequest);

            // Extract statusChangedTags as a List<LegalTagChanged> from the data object
            JsonElement legalTagsChangedMessageElement = legalTagsChangedMessage.getData() != null ? legalTagsChangedMessage.getData().get("statusChangedTags") : null;
            if (legalTagsChangedMessageElement == null || legalTagsChangedMessageElement.isJsonNull()) {
                logger.info(String.format("Message in service bus does not contain statusChangedTags. Ignoring message with id: %s", message.getMessageId()));
                return;
            }

            LegalTagChangedCollection tags = new LegalTagChangedCollection();
            for (JsonElement tagElement : legalTagsChangedMessageElement.getAsJsonArray()) {
                LegalTagChanged legalTagChanged = gson.fromJson(tagElement, LegalTagChanged.class);
                if (legalTagChanged.getChangedTagStatus().equalsIgnoreCase(LegalCompliance.incompliant.toString())) {
                    logger.info(String.format("Deleting associated records for the incompliant legal tag: %s", legalTagChanged.getChangedTagName()));
                    tags.getStatusChangedTags().add(legalTagChanged);
                }
            }

            String correlationId = legalTagsChangedMessage.getCorrelationId();
            String dataPartitionId = legalTagsChangedMessage.getDataPartitionId();
            String user = legalTagsChangedMessage.getUser();

            headers.setThreadContext(dataPartitionId, correlationId, user);
            MDC.setContextMap(mdcContextMap.getContextMap(correlationId, dataPartitionId));
            complianceMessagePullReceiver.receiveMessage(tags, headers);
        } catch (IllegalArgumentException ex) {
            logger.error(String.format("Error occurred when parsing the legal tags changed request from the service bus: %s", ex.getMessage()), ex);
            throw ex;
        } catch (JsonParseException ex) { // This will catch JsonSyntaxException as well since it's a subclass of JsonParseException
            logger.error(String.format("Error occurred when parsing the JSON message from the service bus: %s", ex.getMessage()), ex);
            throw ex;
        } catch (AppException ex) {
            logger.error(String.format("Error occurred when updating compliance on records: %s", ex.getMessage()), ex);
            throw ex;
        } catch (ServiceBusInvalidMessageBodyException ex) {
            logger.error(String.format("Error occurred when retrieving the message from the service bus: %s", ex.getMessage()), ex);
            throw ex;
        } catch (Exception ex) {
            logger.error(String.format("Error occurred when updating compliance on records: %s", ex.getMessage()), ex);
            throw ex;
        } finally {
            ThreadScopeContextHolder.getContext().clear();
            MDC.clear();
        }
    }

    private LegalTagsChangedRequest.Message getLegalTagsChangedMessage(LegalTagsChangedRequest legalTagsChangedRequest) throws ServiceBusInvalidMessageBodyException {
        LegalTagsChangedRequest.Message legalTagsChangedMessage = legalTagsChangedRequest.getMessage();
        if (legalTagsChangedMessage == null) {
            throw new ServiceBusInvalidMessageBodyException("The service bus message field is null in the legal tags changed request");
        }
        return legalTagsChangedMessage;
    }

    private LegalTagsChangedRequest getLegalTagsChangedRequest(JsonElement messageBodyAsJson) throws IllegalArgumentException {
        final String legalTagsJsonExceptionMessage = "The legal tags changed request in the service " +
                "bus message body could not be parsed into LegalTagsChangedRequest";
        LegalTagsChangedRequest legalTagsChangedRequest = null;
        try {
            // gson throws an exception if messageBodyAsJson is not a valid JSON of LegalTagsChangedRequest.
            legalTagsChangedRequest = gson.fromJson(messageBodyAsJson, LegalTagsChangedRequest.class);

            // gson returns null if messageBodyAsJson is an empty or null.
            if (legalTagsChangedRequest == null) {
                throw new IllegalArgumentException(legalTagsJsonExceptionMessage);
            }
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException(
                legalTagsJsonExceptionMessage + " due to invalid json: " + ex.getMessage()
            );
        }

        return legalTagsChangedRequest;
    }

    private String getMessageBodyAsString(IMessage message) throws ServiceBusInvalidMessageBodyException {
        if (message == null || message.getMessageBody() == null) {
            throw new ServiceBusInvalidMessageBodyException("Service bus message body is null");
        }
        List<byte[]> messageBinary = message.getMessageBody().getBinaryData();

        if (messageBinary == null || messageBinary.isEmpty()) {
            throw new ServiceBusInvalidMessageBodyException(String.format("The service bus message ID body is empty or null"));
        }
        return new String(messageBinary.get(0), StandardCharsets.UTF_8);
    }
}
