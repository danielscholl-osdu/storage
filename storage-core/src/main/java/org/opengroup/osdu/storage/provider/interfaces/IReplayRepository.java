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

package org.opengroup.osdu.storage.provider.interfaces;

import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@ConditionalOnProperty(value = "feature.replay.enabled", havingValue = "true", matchIfMissing = false)
public interface IReplayRepository {

    List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId);

    ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId);

    ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaData);
}
