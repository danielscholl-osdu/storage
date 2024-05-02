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

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.dto.ReplayStatus;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ReplayUtils {

    public static Map<String, String> createHeaders(String dataPartitionId, String correlationId) {
        Map<String, String> headers = new HashMap<>();
        headers.put(DpsHeaders.DATA_PARTITION_ID, dataPartitionId);
        headers.put(DpsHeaders.CORRELATION_ID, correlationId);
        return headers;
    }

    public static String getNextCorrelationId(String correlationId, Optional<Integer> kindCounter) {


        if (kindCounter.isPresent()) {
            correlationId = correlationId.substring(0, Math.min(correlationId.length(), 64));
            return correlationId + "_kind_" + kindCounter.get() + "_SEQ_0";
        }

        String[] split = correlationId.split("_SEQ_");
        int seqNo = Integer.parseInt(split[1]) + 1;
        return split[0] + "_SEQ_" + seqNo;
    }

    public static ReplayStatus convertToReplayStatusDTO(ReplayMetaDataDTO replayMetaDataDTO) {

        return ReplayStatus.builder().kind(replayMetaDataDTO.getKind()).
                           state(replayMetaDataDTO.getState()).
                           startedAt(replayMetaDataDTO.getStartedAt()).
                           totalRecords(replayMetaDataDTO.getTotalRecords()).
                           processedRecords(replayMetaDataDTO.getProcessedRecords()).
                           elapsedTime(replayMetaDataDTO.getElapsedTime()).build();
    }

    public static Date formatMillisToDate(long timeMillis) {

        Instant instant = Instant.ofEpochMilli(timeMillis);
        return Date.from(instant);
    }

    public static String formatMillisToHoursMinutesSeconds(long elapsedMillis) {

        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis);

        return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
    }
}
