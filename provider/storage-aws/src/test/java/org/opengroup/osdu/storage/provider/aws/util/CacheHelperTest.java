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

package org.opengroup.osdu.storage.provider.aws.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.storage.provider.aws.cache.GroupCache;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheHelperTest {

    @InjectMocks
    private CacheHelper cacheHelper;

    @Mock
    private GroupCache groupCache;

    @Mock
    private DpsHeaders headers;

    @BeforeEach
    void setUp() {
        openMocks(this);
    }
    @Test
    void getGroupCacheKey_shouldReturnExpectedValue() {
        try (MockedStatic<GroupCache> mocked = Mockito.mockStatic(GroupCache.class)) {
            mocked.when(() -> GroupCache.getGroupCacheKey(headers)).thenReturn("expectedGroupCacheKey");

            String result = cacheHelper.getGroupCacheKey(headers);
            
            assertEquals("expectedGroupCacheKey", result);
        }
    }

    @Test
    void getPartitionGroupsCacheKey_shouldReturnExpectedValue() {
        String dataPartitionId = "somePartitionId";
        try (MockedStatic<GroupCache> mocked = Mockito.mockStatic(GroupCache.class)) {
            mocked.when(() -> GroupCache.getPartitionGroupsCacheKey(dataPartitionId)).thenReturn("expectedPartitionGroupsCacheKey");

            String result = cacheHelper.getPartitionGroupsCacheKey(dataPartitionId);

            assertEquals("expectedPartitionGroupsCacheKey", result);
        } 
    }
}