package org.opengroup.osdu.storage.provider.azure.repository;

import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.azure.di.AzureBootstrapConfig;
import org.opengroup.osdu.storage.provider.azure.di.CosmosContainerConfig;
import org.opengroup.osdu.storage.provider.azure.di.TenantInfoDoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
public class TenantInfoRepository extends SimpleCosmosStoreRepository<TenantInfoDoc> {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private AzureBootstrapConfig azureBootstrapConfig;

    @Autowired
    private CosmosContainerConfig cosmosContainerConfig;

    @Autowired
    private String tenantInfoCollection;

    @Autowired
    private String cosmosDBName;

    @Autowired
    private JaxRsDpsLog logger;

    public TenantInfoRepository() {
        super(TenantInfoDoc.class);
    }

    public Optional<TenantInfoDoc> findById(@NonNull String id) {
        return this.findById(id, headers.getPartitionId(), cosmosDBName, tenantInfoCollection, id);
     }

    public Iterable<TenantInfoDoc> findAll() {
        return this.findAllItems(headers.getPartitionId(), cosmosDBName, tenantInfoCollection);
    }

}
