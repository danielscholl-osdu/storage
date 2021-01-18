/*
 * Copyright 2021 Google LLC
 * Copyright 2021 EPAM Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  private final IMessageFactory messageQueue;

  @Autowired
  public MessageBusImpl(IMessageFactory messageQueue) {
    this.messageQueue = messageQueue;
  }

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
      message.put(DpsHeaders.AUTHORIZATION, headers.getAuthorization());
      messageQueue.sendMessage(gson.toJson(message));
    }
  }
}

