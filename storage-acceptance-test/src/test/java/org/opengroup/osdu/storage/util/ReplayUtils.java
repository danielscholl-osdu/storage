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

package org.opengroup.osdu.storage.util;

import java.util.List;
import org.opengroup.osdu.core.test.client.model.replay.InvalidReplayRequest;
import org.opengroup.osdu.core.test.client.model.replay.ReplayFilter;
import org.opengroup.osdu.core.test.client.model.replay.ReplayRequest;
import org.opengroup.osdu.storage.model.search.SearchCountRequest;

public class ReplayUtils {

  public static ReplayRequest emptyReplayRequest() {
    return new ReplayRequest(null, null);
  }

  public static ReplayRequest replayRequest(String operation) {
    return new ReplayRequest(operation, null);
  }

  public static ReplayRequest replayRequest(String operation, List<String> kindList) {
    return new ReplayRequest(operation, new ReplayFilter(kindList.toArray(String[]::new)));
  }

  public static InvalidReplayRequest replayRequestWithUnknownProperty() {
    return new InvalidReplayRequest("reindex", List.of());
  }

  public static SearchCountRequest searchCountRequest(String kind) {
    return new SearchCountRequest(kind, 1, true);
  }
}
