package org.opengroup.osdu.storage.di;

import org.opengroup.osdu.core.common.http.json.HttpResponseBodyMapper;
import org.opengroup.osdu.core.common.policy.IPolicyFactory;
import org.opengroup.osdu.core.common.policy.PolicyAPIConfig;
import org.opengroup.osdu.core.common.policy.PolicyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;

public class PolicyClientFactory extends AbstractFactoryBean<IPolicyFactory> {

    @Autowired
    private PolicyServiceConfiguration serviceConfiguration;

    @Autowired
    private HttpResponseBodyMapper httpResponseBodyMapper;

    @Override
    public Class<?> getObjectType() {
        return IPolicyFactory.class;
    }

    @Override
    protected IPolicyFactory createInstance() throws Exception {
        PolicyAPIConfig apiConfig = PolicyAPIConfig.builder()
                .rootUrl(serviceConfiguration.getPolicyApiEndpoint())
                .build();
        return new PolicyFactory(apiConfig, httpResponseBodyMapper);
    }
}
