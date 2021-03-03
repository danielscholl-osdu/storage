package org.opengroup.osdu.storage.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class StorageFilterTest {

    @Mock
    private DpsHeaders dpsHeaders;

    @InjectMocks
    private StorageFilter storageFilter;

    @Test
    public void shouldSetCorrectResponseHeaders() throws IOException, ServletException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");

        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Origin", "custom-domain");
        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey");
        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH");
        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Credentials", "true");
        Mockito.verify(httpServletResponse).setHeader("X-Frame-Options", "DENY");
        Mockito.verify(httpServletResponse).setHeader("X-XSS-Protection", "1; mode=block");
        Mockito.verify(httpServletResponse).setHeader("X-Content-Type-Options", "nosniff");
        Mockito.verify(httpServletResponse).setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        Mockito.verify(httpServletResponse).setHeader("Content-Security-Policy", "default-src 'self'");
        Mockito.verify(httpServletResponse).setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        Mockito.verify(httpServletResponse).setHeader("Expires", "0");
        Mockito.verify(httpServletResponse).setHeader("correlation-id", "correlation-id-value");
        Mockito.verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }
}
