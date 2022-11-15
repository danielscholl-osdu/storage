package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.tenant.TenantInfo;
import org.opengroup.osdu.storage.response.ErrorResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class StorageFilterTest {
    private static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";
    private static final String COLLABORATION_DIRECTIVES = "id=8e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=Unit test";

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpServletResponse httpServletResponse;

    @Mock
    private PrintWriter writer;

    @Mock
    private FilterChain filterChain;
    
    @Mock
    private DpsHeaders dpsHeaders;

    @InjectMocks
    private StorageFilter storageFilter;

    @Before
    public void setup() {
        initMocks(this);
        
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "isCollaborationEnabled", false);
    }

    @Test
    public void shouldSetCorrectResponseHeaders() throws IOException, ServletException {
        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Origin", "custom-domain");
        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Headers", "access-control-allow-origin, origin, content-type, accept, authorization, data-partition-id, correlation-id, appkey");
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

    @Test
    public void shouldThrowException_ifCollaborationHeaderProvided_whenCollaborationFeatureFlagDisabled() throws IOException, ServletException {
        Mockito.when(httpServletRequest.getHeader(X_COLLABORATION_HEADER_NAME)).thenReturn(COLLABORATION_DIRECTIVES);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(writer);

        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        Mockito.verify(httpServletResponse).setHeader("Access-Control-Allow-Origin", "custom-domain");
        Mockito.verify(httpServletResponse).setContentType("application/json");
        Mockito.verify(httpServletResponse).setStatus(HttpStatus.SC_LOCKED);
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.SC_LOCKED, "Locked","Feature is not enabled on this environment");
        Mockito.verify(writer).write(errorResponse.toString());
    }
}
