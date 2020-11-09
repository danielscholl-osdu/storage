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

package org.opengroup.osdu.storage.provider.azure.di;

import com.azure.security.keyvault.secrets.SecretClient;
import org.opengroup.osdu.azure.KeyVaultFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.inject.Named;


@Component
public class AzureBootstrapConfig {

    @Value("${azure.servicebus.topic-name}")
    private String serviceBusTopic;

    @Bean
    @Named("STORAGE_CONTAINER_NAME")
    public String containerName() {
        return "opendes";
    }

    @Bean
    @Named("SERVICE_BUS_TOPIC")
    public String serviceBusTopic() {
        return serviceBusTopic;
    }

    @Value("${azure.keyvault.url}")
    private String keyVaultURL;

    @Value("${azure.cosmosdb.database}")
    private String cosmosDBName;

    @Bean
    @Named("KEY_VAULT_URL")
    public String keyVaultURL() {
        return keyVaultURL;
    }

    @Bean
    public String cosmosDBName() {
        return cosmosDBName;
    }

    @Value("${redis.port:6380}")
    public int redisPort;

    @Bean
    @Named("REDIS_PORT")
    public int getRedisPort() {
        return redisPort;
    }

    @Value("${redis.timeout:3600}")
    public int redisTimeout;

    @Bean
    @Named("REDIS_TIMEOUT")
    public int getRedisTimeout() {
        return redisTimeout;
    }

    @Bean
    @Named("REDIS_HOST")
    public String redisHost(SecretClient kv) {
        return KeyVaultFacade.getSecretWithValidation(kv, "redis-hostname");
    }

    @Bean
    @Named("REDIS_PASSWORD")
    public String redisPassword(SecretClient kv) {
        return KeyVaultFacade.getSecretWithValidation(kv, "redis-password");
    }

}