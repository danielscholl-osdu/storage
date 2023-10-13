package org.opengroup.osdu.storage.provider.aws.cache;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.sql.Ref;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opengroup.osdu.core.aws.ssm.K8sLocalParameterProvider;
import org.opengroup.osdu.core.aws.ssm.K8sParameterNotFoundException;
import org.opengroup.osdu.core.common.cache.ICache;
import org.opengroup.osdu.core.common.cache.MultiTenantCache;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

class LegalTagCacheTest {

    @Mock
    private MultiTenantCache<String> caches;

    @Mock
    private ICache<String, String> partitionCache;

    @Mock
    private K8sLocalParameterProvider provider;

    @Mock
    private TenantInfo tenant;

    private LegalTagCache legalTagCache;

    @BeforeEach
    void setUp() throws JsonProcessingException, K8sParameterNotFoundException {
        MockitoAnnotations.openMocks(this);
        when(tenant.toString()).thenReturn("tenant1");
        when(provider.getLocalMode()).thenReturn(true);
        when(caches.get(anyString())).thenReturn(partitionCache);
        legalTagCache = new LegalTagCache(provider, "true");
        ReflectionTestUtils.setField(legalTagCache, "caches", caches);
        ReflectionTestUtils.setField(legalTagCache, "tenant", tenant);
    }

    @Test
    void put_shouldPutValueIntoPartitionCache() {
        String testKey = "testKey";
        String testVal = "testVal";

        legalTagCache.put(testKey, testVal);


    verify(partitionCache, times(1)).put(testKey, testVal); 
    }

    @Test
    void get_shouldGetValueFromPartitionCache() {
        legalTagCache.get("testKey");

        verify(partitionCache, times(1)).get("testKey");
    }

    @Test
    void delete_shouldDeleteKeyFromPartitionCache() {
        legalTagCache.delete("testKey");

        verify(partitionCache, times(1)).delete("testKey");
    }

    @Test
    void clearAll_shouldClearAllEntriesInPartitionCache() {
        legalTagCache.clearAll();

        verify(partitionCache, times(1)).clearAll();
    }
}