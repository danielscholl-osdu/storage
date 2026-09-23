package org.opengroup.osdu.storage.provider.azure.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.azure.util.AzureServicePrincipleTokenService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ServiceAccountJwtClientImplTest {

    @Mock
    AzureServicePrincipleTokenService azureServicePrincipleTokenService;
    @InjectMocks
    ServiceAccountJwtClientImpl sut;

    @Test
    void getIDToken_callsAzurePrincipleTokenService_andReturnsBearerToken() {
        String token = sut.getIdToken("partition");

        verify(azureServicePrincipleTokenService, times(1)).getAuthorizationToken();
        assertTrue(token.contains("Bearer "));
    }
}
