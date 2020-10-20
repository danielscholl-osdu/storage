package org.opengroup.osdu.storage.provider.reference.service;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.reference.messagebus.IMessageFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageBusImpl implements IMessageBus {

  @Autowired
  private IMessageFactory messageQueue;

  public void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
    final int BATCH_SIZE = 50;
    Map<String, String> message = new HashMap<>();
    Gson gson = new Gson();

    for (int i = 0; i < messages.length; i += BATCH_SIZE) {
      PubSubInfo[] batch = Arrays
          .copyOfRange(messages, i, Math.min(messages.length, i + BATCH_SIZE));

      String json = gson.toJson(batch);
      message.put("data", json);
      message.put(DpsHeaders.DATA_PARTITION_ID, headers.getPartitionIdWithFallbackToAccountId());
      headers.addCorrelationIdIfMissing();
      message.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());

      messageQueue.sendMessage(gson.toJson(message));
    }
  }
}

