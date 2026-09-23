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

package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.storage.provider.azure.model.ReplayMetaData;
import org.opengroup.osdu.storage.provider.interfaces.IReplayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ReplayRepository extends SimpleCosmosStoreRepository<ReplayMetaData> implements IReplayRepository {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private CosmosContainerConfig cosmosContainerConfig;

    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;

    public ReplayRepository() {
        super(ReplayMetaData.class);
    }


    @Override
    public List<ReplayMetaDataDTO> getReplayStatusByReplayId(String replayId) {

        SqlQuerySpec query = new SqlQuerySpec("SELECT * FROM c WHERE c.replayId= @replayId");
        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@replayId", replayId));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<ReplayMetaData> queryResults = this.queryItems(headers.getPartitionId(),
                azureBootstrapConfig.getCosmosDBName(),
                cosmosContainerConfig.getReplayCollectionName(),
                query,
                options);
        List<ReplayMetaDataDTO> replayMetaDataDTOList = new ArrayList<>();
        for (ReplayMetaData replayMetaData : queryResults) {
            replayMetaDataDTOList.add(this.getReplayMetaDataDTOObject(replayMetaData));
        }
        return replayMetaDataDTOList;
    }

    public ReplayMetaDataDTO getReplayStatusByKindAndReplayId(String kind, String replayId) {

        SqlQuerySpec query = new SqlQuerySpec("SELECT * FROM c WHERE c.replayId= @replayId and c.kind= @kind");
        List<SqlParameter> pars = query.getParameters();
        pars.add(new SqlParameter("@replayId", replayId));
        pars.add(new SqlParameter("@kind", kind));
        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        List<ReplayMetaData> queryResults = this.queryItems(headers.getPartitionId(),
                azureBootstrapConfig.getCosmosDBName(),
                cosmosContainerConfig.getReplayCollectionName(),
                query,
                options);
        ReplayMetaData replayMetaData = CollectionUtils.isEmpty(queryResults) ? null : queryResults.get(0);
        return this.getReplayMetaDataDTOObject(replayMetaData);

    }

    @Override
    public ReplayMetaDataDTO save(ReplayMetaDataDTO replayMetaDataDTO) {

        ReplayMetaData entity = getReplayMetaData(replayMetaDataDTO);
        ReplayMetaData replayMetaData = this.save(
                entity,
                headers.getPartitionId(),
                azureBootstrapConfig.getCosmosDBName(),
                cosmosContainerConfig.getReplayCollectionName(),
                entity.getReplayId()
        );
        return this.getReplayMetaDataDTOObject(replayMetaData);
    }

    private ReplayMetaDataDTO getReplayMetaDataDTOObject(ReplayMetaData replayMetaData) {

        if (replayMetaData == null) return null;

        return ReplayMetaDataDTO.builder()
                .id(replayMetaData.getId())
                .replayId(replayMetaData.getReplayId())
                .totalRecords(replayMetaData.getTotalRecords())
                .processedRecords(replayMetaData.getProcessedRecords())
                .operation(replayMetaData.getOperation())
                .state(replayMetaData.getState())
                .elapsedTime(replayMetaData.getElapsedTime())
                .filter(replayMetaData.getFilter())
                .startedAt(replayMetaData.getStartedAt())
                .kind(replayMetaData.getKind())
                .build();
    }

    private ReplayMetaData getReplayMetaData(ReplayMetaDataDTO replayMetaDataDTO) {

        if (replayMetaDataDTO == null) return null;

        return ReplayMetaData.builder()
                .id(replayMetaDataDTO.getId())
                .replayId(replayMetaDataDTO.getReplayId())
                .totalRecords(replayMetaDataDTO.getTotalRecords())
                .processedRecords(replayMetaDataDTO.getProcessedRecords())
                .operation(replayMetaDataDTO.getOperation())
                .state(replayMetaDataDTO.getState())
                .elapsedTime(replayMetaDataDTO.getElapsedTime())
                .filter(replayMetaDataDTO.getFilter())
                .startedAt(replayMetaDataDTO.getStartedAt())
                .kind(replayMetaDataDTO.getKind())
                .build();
    }
}
