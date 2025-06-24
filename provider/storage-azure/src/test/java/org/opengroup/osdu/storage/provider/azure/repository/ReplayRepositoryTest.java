package org.opengroup.osdu.storage.provider.azure.repository;

import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.dto.ReplayMetaDataDTO;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.azure.cosmosdb.CosmosStore;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReplayRepositoryTest {

    @Mock(lenient = true)
    private DpsHeaders dpsHeaders;

    @Mock
    private CosmosContainerConfig cosmosContainerConfig;

    @Mock
    private AzureBootstrapConfig azureBootstrapConfig;

    @Mock
    private CosmosStore cosmosStore;

    @InjectMocks
    ReplayRepository replayRepository;

    private final String REPLAY_ID = UUID.randomUUID().toString();

    private final static String KIND = "opendes:source:type:1.0.0";

    @BeforeEach
    public void setUp() {
        when(dpsHeaders.getPartitionId()).thenReturn("opendes");
    }

    @Test
    void getReplayStatusByReplayId()
    {
        when(azureBootstrapConfig.getCosmosDBName()).thenReturn("osdu-db");
        when(cosmosContainerConfig.getReplayCollectionName()).thenReturn("ReplayStatus");
        List<ReplayMetaDataDTO> replayMetaDataDTOList = replayRepository.getReplayStatusByReplayId(REPLAY_ID);
        verify(cosmosStore).queryItems(
                eq("opendes"),
                eq("osdu-db"),
                eq("ReplayStatus"),
                any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class),
                any()
        );
        assertEquals(0, replayMetaDataDTOList.size());
    }

    @Test
    void getReplayStatusByKindAndReplayId(){

        when(azureBootstrapConfig.getCosmosDBName()).thenReturn("osdu-db");
        when(cosmosContainerConfig.getReplayCollectionName()).thenReturn("ReplayStatus");
        replayRepository.getReplayStatusByKindAndReplayId(KIND, REPLAY_ID);
        verify(cosmosStore).queryItems(
                eq("opendes"),
                eq("osdu-db"),
                eq("ReplayStatus"),
                any(SqlQuerySpec.class),
                any(CosmosQueryRequestOptions.class),
                any());
    }

    @Test
    void save() {

        ReplayMetaDataDTO replayMetaData = new ReplayMetaDataDTO();
        replayMetaData.setReplayId(REPLAY_ID);
        ReplayMetaDataDTO replayMetaDataDTO= replayRepository.save(replayMetaData);
        assertEquals(REPLAY_ID,replayMetaDataDTO.getReplayId());
    }
}
