package org.opengroup.osdu.storage.provider.azure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadScopeTest {
    @Mock
    ObjectFactory<?> factory;
    @Mock
    Object obj;
    @InjectMocks
    ThreadScope sut;
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
    private final String name = "name";

    @Test
    void getObjectShouldCleanupContext_AndReturnNonNullObjectOfTypeDpsHeaders() {
        Map<String, String> headers = getSomeHeadersInAMap();
        Enumeration<String> headerNames = Collections.enumeration(headers.keySet());
        when(request.getHeaderNames()).thenReturn(headerNames);
        when(request.getHeader(any())).thenReturn("header");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        obj = sut.get("bean-name", factory);

        assertNotNull(obj);
        assertEquals(obj.getClass(), dpsHeaders.getClass());
    }

    @Test
    void get_shouldReturnObjectFromSuppliedFactory_ifRequestAttributesAreMissing() {
        RequestContextHolder.resetRequestAttributes();
        ObjectFactory<Object> objectFactory = mock(ObjectFactory.class);
        when(objectFactory.getObject()).thenReturn(obj);

        Object objOut = sut.get(name, objectFactory);

        assertEquals(obj, objOut);
    }

    private Map<String, String> getSomeHeadersInAMap() {
        Map<String, String> headers = new HashMap<>();
        headers.put("header1", "value1");
        headers.put("Content-Type", "text/html");
        return headers;
    }
}
