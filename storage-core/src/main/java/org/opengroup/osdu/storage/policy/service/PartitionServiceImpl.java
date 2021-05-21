// Copyright © Schlumberger
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

package org.opengroup.osdu.storage.policy.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.partition.IPartitionFactory;
import org.opengroup.osdu.core.common.partition.IPartitionProvider;
import org.opengroup.osdu.core.common.partition.PartitionException;
import org.opengroup.osdu.core.common.partition.PartitionInfo;
import org.opengroup.osdu.core.common.util.IServiceAccountJwtClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(value = "service.policy.enabled", havingValue = "true", matchIfMissing = false)
public class PartitionServiceImpl implements IPartitionService {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private IPartitionFactory factory;

    @Autowired
    private IServiceAccountJwtClient tokenService;

    @Override
    public PartitionInfo getPartition(String partitionId) {
        try {
//            this.headers.put(DpsHeaders.AUTHORIZATION, this.tokenService.getIdToken(this.headers.getPartitionId()));
        	Map<String,String> partitionHeadersMap = new HashMap<>();
        	partitionHeadersMap.put(DpsHeaders.CONTENT_TYPE,headers.getContentType());
        	partitionHeadersMap.put(DpsHeaders.CORRELATION_ID, headers.getCorrelationId());
        	partitionHeadersMap.put(DpsHeaders.AUTHORIZATION, this.tokenService.getIdToken(this.headers.getPartitionId()));
    		DpsHeaders partitionHeaders = DpsHeaders.createFromMap(partitionHeadersMap);
    		
            IPartitionProvider serviceClient = this.factory.create(partitionHeaders);
            PartitionInfo partitionInfo = serviceClient.get(partitionId);
            return partitionInfo;
        } catch (PartitionException e) {
            throw new AppException(HttpStatus.SC_FORBIDDEN, "Service unavailable", String.format("Error getting partition info for data-partition: %s", partitionId), e);
        }
    }
}

