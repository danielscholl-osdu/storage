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