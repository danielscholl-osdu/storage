package org.opengroup.osdu.storage.provider.azure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class ThreadScopeTest {
    private String name = "name";
    private ThreadScope threadScope = new ThreadScope();
    @Mock
    ObjectFactory<?> factory = null;
    @Mock
    Object obj;
    @Mock
    ThreadScope scope;
    @Mock
    ThreadScopeContext context;
    @Mock
    ThreadDpsHeaders threadDpsHeaders;
    @Mock
    DpsHeaders dpsHeaders;
    @Mock
    MockHttpServletRequest request;
    @Mock
    MDC mdcContext;

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
    @Test
    void shouldGetObjectofDpsHeaderClass(){
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("Content-Type", "text/html");
        Enumeration<String> headerNames = Collections.enumeration(headers.keySet());
        when(request.getHeaderNames()).thenReturn(headerNames);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader(any())).thenReturn(any());
        assertEquals(threadScope.get(name,factory).getClass(),dpsHeaders.getClass());
    }
}
