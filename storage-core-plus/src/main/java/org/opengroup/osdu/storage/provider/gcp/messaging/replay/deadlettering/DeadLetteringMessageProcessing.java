/*
 *  Copyright 2020-2025 Google LLC
 *  Copyright 2020-2025 EPAM Systems, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.opengroup.osdu.storage.provider.gcp.messaging.replay.deadlettering;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.oqm.core.model.OqmMessage;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.provider.gcp.messaging.replay.ReplayMessageService;

@RequiredArgsConstructor
@Slf4j
public class DeadLetteringMessageProcessing {
  private final ReplayMessageService replayMessageService;
  private final DpsHeaders dpsHeaders;
  private final Type listType = new TypeToken<List<ReplayMessage>>() {}.getType();
  private final Gson gson = new Gson();

  public void process(OqmMessage oqmMessage) {
    String pubSubMessage = oqmMessage.getData();
    List<ReplayMessage> replayMessageList = gson.fromJson(pubSubMessage, listType);
    for (ReplayMessage replayMessage : replayMessageList) {
      dpsHeaders.put(DpsHeaders.DATA_PARTITION_ID, replayMessage.getDataPartitionId());
      dpsHeaders.put(DpsHeaders.CORRELATION_ID, replayMessage.getCorrelationId());
      log.debug("* * Dead Lettering: Message = {}", replayMessage.getBody());
      replayMessageService.processFailure(replayMessage);
    }
  }
}
