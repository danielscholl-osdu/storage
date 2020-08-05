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

        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        Mockito.verify(httpServletResponse).addHeader("Access-Control-Allow-Origin", Collections.singletonList("*").toString());
        Mockito.verify(httpServletResponse).addHeader("Access-Control-Allow-Headers", Collections.singletonList("origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey").toString());
        Mockito.verify(httpServletResponse).addHeader("Access-Control-Allow-Methods", Collections.singletonList("GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH").toString());
        Mockito.verify(httpServletResponse).addHeader("Access-Control-Allow-Credentials", Collections.singletonList("true").toString());
        Mockito.verify(httpServletResponse).addHeader("X-Frame-Options", Collections.singletonList("DENY").toString());
        Mockito.verify(httpServletResponse).addHeader("X-XSS-Protection", Collections.singletonList("1; mode=block").toString());
        Mockito.verify(httpServletResponse).addHeader("X-Content-Type-Options", Collections.singletonList("nosniff").toString());
        Mockito.verify(httpServletResponse).addHeader("Cache-Control", Collections.singletonList("no-cache, no-store, must-revalidate").toString());
        Mockito.verify(httpServletResponse).addHeader("Content-Security-Policy", Collections.singletonList("default-src 'self'").toString());
        Mockito.verify(httpServletResponse).addHeader("Strict-Transport-Security", Collections.singletonList("max-age=31536000; includeSubDomains").toString());
        Mockito.verify(httpServletResponse).addHeader("Expires", Collections.singletonList("0").toString());
        Mockito.verify(httpServletResponse).addHeader("correlation-id", "correlation-id-value");
        Mockito.verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
    }
}
