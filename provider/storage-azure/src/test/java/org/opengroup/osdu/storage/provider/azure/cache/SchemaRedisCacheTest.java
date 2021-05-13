package org.opengroup.osdu.storage.provider.azure.cache;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengroup.osdu.core.common.cache.RedisCache;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.entitlements.Groups;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberMatcher;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SchemaRedisCache.class)
public class SchemaRedisCacheTest {

    @Test
    public void ifRedisReturnValue_whenGet_thenReturnTheCache() {
        PowerMockito.suppress(MemberMatcher.constructorsDeclaredIn(RedisCache.class));
        JaxRsDpsLog log = mock(JaxRsDpsLog.class);
        SchemaRedisCache schemaRedisCache = spy(new SchemaRedisCache("", 0, "", 0, 0, log));
        Schema schema = mock(Schema.class);
        doReturn(schema).when((RedisCache)schemaRedisCache).get("test-key");
        assertEquals(schema, schemaRedisCache.get("test-key"));
    }

    @Test
    public void ifRedisThrowException_whenGet_thenReturnNull() {
        PowerMockito.suppress(MemberMatcher.constructorsDeclaredIn(RedisCache.class));
        JaxRsDpsLog log = mock(JaxRsDpsLog.class);
        SchemaRedisCache schemaRedisCache = spy(new SchemaRedisCache("", 0, "", 0, 0, log));
        doThrow(new Exception()).when((RedisCache)schemaRedisCache).get("test-key");
        assertEquals(null, schemaRedisCache.get("test-key"));
    }
}
