package org.opengroup.osdu.storage.provider.azure.di;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosmosContainerConfig {

    @Value("${azure.cosmosdb.schema.collection}")
    private String schemaCollectionName;

    @Value("${azure.cosmosdb.recordmetadata.collection}")
    private String recordMetadataCollectionName;

    @Value("${azure.cosmosdb.tenantinfo.collection}")
    private String tenantInfoCollectionName;

    @Bean
    public String schemaCollection() {
        return schemaCollectionName;
    }

    @Bean
    public String recordMetadataCollection() {
        return recordMetadataCollectionName;
    }

    @Bean
    public String tenantInfoCollection() {
        return tenantInfoCollectionName;
    }

}
