package org.opengroup.osdu.storage.provider.gcp.osm;

import lombok.RequiredArgsConstructor;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.core.common.provider.interfaces.ITenantFactory;
import org.opengroup.osdu.core.gcp.osm.model.Destination;
import org.opengroup.osdu.core.gcp.osm.model.Query;
import org.opengroup.osdu.core.gcp.osm.translate.datastore.DsDestinationResolution;
import org.opengroup.osdu.core.gcp.osm.translate.datastore.DsDestinationResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * @author Rostislav_Dublin
 * @since 15.09.2021
 */
@Component
@Scope(SCOPE_SINGLETON)
@ConditionalOnProperty(name = "osmDriver", havingValue = "datastore")
@RequiredArgsConstructor
public class DsTenantDestinationResolver implements DsDestinationResolver {

    private final ITenantFactory tenantInfoFactory;

    @Override
    public DsDestinationResolution resolve(Destination destination) {
        TenantInfo ti = tenantInfoFactory.getTenantInfo(destination.getPartitionId());
        return DsDestinationResolution.builder().projectId(ti.getProjectId()).build();
    }
}
