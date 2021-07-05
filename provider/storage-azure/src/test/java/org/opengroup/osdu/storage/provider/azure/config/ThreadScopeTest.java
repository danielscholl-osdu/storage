package org.opengroup.osdu.storage.provider.azure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class ThreadScopeTest {
    private String name = "name";
    @Mock
    ObjectFactory<?> factory = null;
    @Mock
    Object obj;
    @Mock
    ThreadScope scope;
    @Mock
    ThreadScopeContext context;

    @Test
    void shouldGetObject() {
        when(scope.get(name,null)).thenReturn(obj);
        obj = scope.get(name,factory);
        assertNotNull(obj);
    }
    @Test
    void shouldNotGetObject(){
        when(scope.get(any(),any())).thenReturn(null);
        obj = scope.get(any(),any());
        assertNull(obj);
    }

}