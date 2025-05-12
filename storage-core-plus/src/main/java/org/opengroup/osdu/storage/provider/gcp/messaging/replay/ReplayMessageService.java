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

package org.opengroup.osdu.storage.provider.gcp.messaging.replay;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengroup.osdu.storage.dto.ReplayMessage;
import org.opengroup.osdu.storage.service.replay.IReplayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope(SCOPE_SINGLETON)
@RequiredArgsConstructor
@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true")
public class ReplayMessageService {

  private final IReplayService replayService;

  public void processReplayMessage(ReplayMessage replayMessage) {
    replayService.processReplayMessage(replayMessage);
  }

  public void processFailure(ReplayMessage replayMessage) {
    replayService.processFailure(replayMessage);
  }
}
