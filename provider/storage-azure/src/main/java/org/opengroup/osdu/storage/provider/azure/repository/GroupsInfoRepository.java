package org.opengroup.osdu.storage.provider.azure.repository;

import org.opengroup.osdu.azure.multitenancy.TenantInfoDoc;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class GroupsInfoRepository extends SimpleCosmosStoreRepository<TenantInfoDoc> {

    @Autowired
    private DpsHeaders headers;

    @Autowired
    private String tenantInfoCollection;

    @Autowired
    private String cosmosDBName;

    public GroupsInfoRepository() {
        super(TenantInfoDoc.class);
    }

    public Optional<TenantInfoDoc> findById(@NonNull String id) {
        return this.findById(id, headers.getPartitionId(), cosmosDBName, tenantInfoCollection, id);
    }
}
