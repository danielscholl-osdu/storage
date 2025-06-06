/* Licensed Materials - Property of IBM              */
/* (c) Copyright IBM Corp. 2020. All Rights Reserved.*/

package org.opengroup.osdu.storage.provider.ibm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.ibm.messagebus.IMessageFactory;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;

@Component
public class MessageBusImpl implements IMessageBus {

	@Inject
	IMessageFactory mq;

	public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {

		final int BATCH_SIZE = 50;
		Map<String, String> message = new HashMap<>();
		Gson gson = new Gson();

		for (int i = 0; i < messages.length; i += BATCH_SIZE) {

			PubSubInfo[] batch = Arrays.copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

			String json = gson.toJson(batch);
			message.put("data", json);
			message.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
			headers.addCorrelationIdIfMissing();
			message.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());

			mq.sendMessage(gson.toJson(message));
		}

	}

	@Override
	public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {
		throw new  NotImplementedException();
	}

	@Override
	public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
		throw new  NotImplementedException();
	}

	@Override
	public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, PubSubInfo... messages) {
		throw new NotImplementedException();
	}

}
