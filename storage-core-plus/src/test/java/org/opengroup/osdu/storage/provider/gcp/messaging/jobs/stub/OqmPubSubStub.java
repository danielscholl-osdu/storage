/*
 *  Copyright 2020-2022 Google LLC
 *  Copyright 2020-2022 EPAM Systems, Inc
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

package org.opengroup.osdu.storage.provider.gcp.messaging.jobs.stub;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
import org.opengroup.osdu.core.common.model.http.CollaborationContext;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.storage.model.RecordChangedV2;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;

@Getter
public class OqmPubSubStub implements IMessageBus {

    private final List<Map<DpsHeaders, PubSubInfo[]>> collector = new ArrayList<>();

    @Override
    public synchronized void publishMessage(DpsHeaders headers, PubSubInfo... messages) {
        collector.add(ImmutableMap.of(DpsHeaders.createFromMap(headers.getHeaders()), messages));
    }

    @Override
    public void publishMessage(Optional<CollaborationContext> collaborationContext, DpsHeaders headers, RecordChangedV2... messages) {
        throw new NotImplementedException();
    }

    @Override
    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, List<?> messageList) {
        throw new NotImplementedException();
    }

    @Override
    public void publishMessage(DpsHeaders headers, Map<String, String> routingInfo, PubSubInfo... messages) {
        throw new NotImplementedException();
    }
}
