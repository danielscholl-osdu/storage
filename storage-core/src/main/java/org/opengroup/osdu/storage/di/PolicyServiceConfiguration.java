package org.opengroup.osdu.storage.di;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@Getter
@Lazy
public class PolicyServiceConfiguration {

    @Value("${POLICY_ID}")
    private String policyId;

    @Value("${POLICY_API}")
    private String policyApiEndpoint;
}
