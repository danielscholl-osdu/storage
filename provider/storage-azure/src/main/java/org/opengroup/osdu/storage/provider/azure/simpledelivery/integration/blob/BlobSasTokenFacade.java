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

package org.opengroup.osdu.storage.provider.azure.simpledelivery.integration.blob;

import com.azure.identity.DefaultAzureCredential;
import com.azure.storage.blob.*;
import com.azure.storage.blob.models.UserDelegationKey;
import com.azure.storage.blob.sas.BlobContainerSasPermission;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/*
For a given blob object, generator a SAS Token that'll let bearers access the blob for 24 hours.
 */
@Log
@Component
public class BlobSasTokenFacade {

    @Inject
    private DefaultAzureCredential defaultCredential;

    public String signContainer(String containerUrl) {
        BlobUrlParts parts = BlobUrlParts.parse(containerUrl);
        String endpoint = calcBlobAccountUrl(parts);

        BlobServiceClient rbacKeySource = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(defaultCredential)
                .buildClient();


        BlobContainerClient blobContainerClient = new BlobContainerClientBuilder()
                .credential(defaultCredential)
                .endpoint(containerUrl)
                .containerName(parts.getBlobContainerName())
                .buildClient();

        // @todo review expiration date for container SAS 
        OffsetDateTime expiresInHalfADay = calcTokenExpirationDate();
        UserDelegationKey key = rbacKeySource.getUserDelegationKey(null, expiresInHalfADay);

        BlobContainerSasPermission perms = BlobContainerSasPermission.parse("lr");
        BlobServiceSasSignatureValues tokenProps = new BlobServiceSasSignatureValues(expiresInHalfADay, perms);
        String sasToken = blobContainerClient.generateUserDelegationSas(tokenProps, key);

        String sasUri = String.format("%s?%s", containerUrl, sasToken);
        return sasUri;
    }

    public String sign(String blobUrl) {

        BlobUrlParts parts = BlobUrlParts.parse(blobUrl);
        String endpoint = calcBlobAccountUrl(parts);

        BlobServiceClient rbacKeySource = new BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(defaultCredential)
                .buildClient();

        BlobClient tokenSource = new BlobClientBuilder()
                .credential(defaultCredential)
                .endpoint(blobUrl)
                .buildClient();

        OffsetDateTime expiresInHalfADay = calcTokenExpirationDate();
        UserDelegationKey key = rbacKeySource.getUserDelegationKey(null, expiresInHalfADay);

        BlobSasPermission readOnlyPerms = BlobSasPermission.parse("r");
        BlobServiceSasSignatureValues tokenProps = new BlobServiceSasSignatureValues(expiresInHalfADay, readOnlyPerms);

        String sasToken = tokenSource.generateUserDelegationSas(tokenProps, key);

        String sasUri = String.format("%s?%s", blobUrl, sasToken);
        return sasUri;
    }

    private String calcBlobAccountUrl(BlobUrlParts parts) {
        return String.format("https://%s.blob.core.windows.net", parts.getAccountName());
    }

    private OffsetDateTime calcTokenExpirationDate() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(12);
    }
}
