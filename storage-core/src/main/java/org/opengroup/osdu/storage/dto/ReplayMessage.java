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

package org.opengroup.osdu.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.enums.ReplayType;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReplayMessage {

    private Map<String, String> headers =  new HashMap<>();

    private ReplayData body;

    public String getDataPartitionId(){
        return getHeader(DpsHeaders.DATA_PARTITION_ID);
    }

    public void setDataPartitionId(String value){
        headers.put(DpsHeaders.DATA_PARTITION_ID, value);
    }

    public String getCorrelationId(){
        return getHeader(DpsHeaders.CORRELATION_ID);
    }

    private String getHeader(String header){
        return headers == null? null : headers.get(header);
    }
}
