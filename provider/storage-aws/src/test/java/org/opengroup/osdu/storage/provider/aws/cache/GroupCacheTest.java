// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opengroup.osdu.storage.provider.aws.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opengroup.osdu.core.aws.cache.DefaultCache;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.util.Crc32c;
import org.springframework.test.context.TestPropertySource;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;

@TestPropertySource(properties = {"aws.elasticache.cluster.endpoint=testHost", "aws.elasticache.cluster.port=1234", "aws.elasticache.cluster.key=testKey"})
class GroupCacheTest {
    
    @Mock
    private K8sLocalParameterProvider provider;
    @Mock
    private DpsHeaders headers;

    private final String dataPartitionId = "testPartitionId";

    private final String authorization = "testAuthorization";

    private static final String REDIS_SEARCH_HOST = "testHost";
    private static final String REDIS_SEARCH_PORT = "1234";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_ENDPOINT", null)).thenReturn(REDIS_SEARCH_HOST);
        when(provider.getParameterAsStringOrDefault("CACHE_CLUSTER_PORT", null)).thenReturn(REDIS_SEARCH_PORT);
        when(provider.getLocalMode()).thenReturn(true);
        when(headers.getPartitionIdWithFallbackToAccountId()).thenReturn(dataPartitionId);
        when(headers.getAuthorization()).thenReturn(authorization);
    }

    @Test
    void should_createGroupCache_withExpectedArguments() throws Throwable {
        assertDoesNotThrow(() -> (new GroupCache<String, Groups>()));
    }

    @Test
    void testGetGroupCacheKey() {
        // Act
        String result = GroupCache.getGroupCacheKey(headers);

        // Assert
        String expectedKey = String.format("entitlement-groups:%s:%s", dataPartitionId, authorization);
        assertEquals(Crc32c.hashToBase64EncodedString(expectedKey), result);
    }

    @Test
    void testGetPartitionGroupsCacheKey() {
        // Act
        String result = GroupCache.getPartitionGroupsCacheKey(dataPartitionId);

        // Assert
        String expectedKey = String.format("entitlement-groups:data-partition:%s", dataPartitionId);
        assertEquals(Crc32c.hashToBase64EncodedString(expectedKey), result);
    }
}
