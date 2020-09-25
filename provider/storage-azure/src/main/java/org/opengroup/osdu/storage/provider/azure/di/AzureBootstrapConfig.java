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

import com.azure.cosmos.ConnectionMode;
import com.azure.cosmos.ConnectionPolicy;
import com.azure.cosmos.internal.AsyncDocumentClient;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.inject.Named;


@Component
public class AzureBootstrapConfig {

    @Value("${azure.storage.account-name}")
    private String storageAccount;

    @Value("${servicebus_topic_name}")
    private String serviceBusTopic;

    @Value("${servicebus_namespace_name}")
    private String serviceBusNamespace;

    @Bean
    @Named("STORAGE_ACCOUNT_NAME")
    public String storageAccount() {
        return storageAccount;
    }

    @Bean
    @Named("STORAGE_CONTAINER_NAME")
    public String containerName() {
        return "opendes";
    }

    @Bean
    @Named("SERVICE_BUS_NAMESPACE")
    public String serviceBusNamespace() {
        return serviceBusNamespace;
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
    @Named("COSMOS_KEY")
    public String cosmosKey(SecretClient kv) {
        return getKeyVaultSecret(kv, "cosmos-primary-key");
    }

    @Bean
    @Named("COSMOS_ENDPOINT")
    public String cosmosEndpoint(SecretClient kv) {
        return getKeyVaultSecret(kv, "cosmos-endpoint");
    }

    @Bean
    public String cosmosDBName() {
        return cosmosDBName;
    }

    public String getKeyVaultSecret(SecretClient kv, String secretName) {
        KeyVaultSecret secret = kv.getSecret(secretName);
        if (secret == null) {
            throw new IllegalStateException(String.format("No secret found with name %s", secretName));
        }

        String secretValue = secret.getValue();
        if (secretValue == null) {
            throw new IllegalStateException(String.format(
                    "Secret unexpectedly missing from KeyVault response for secret with name %s", secretName));
        }

        return secretValue;
    }

    @Bean
    public AsyncDocumentClient asyncDocumentClient(final @Named("COSMOS_ENDPOINT") String endpoint, final @Named("COSMOS_KEY") String key) {

        ConnectionPolicy connectionPolicy = new ConnectionPolicy();
        connectionPolicy.setConnectionMode(ConnectionMode.DIRECT);

        return new AsyncDocumentClient.Builder()
                .withServiceEndpoint(endpoint)
                .withMasterKeyOrResourceToken(key)
                .withConnectionPolicy(connectionPolicy)
                .build();
    }

}