package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CollaborationFilterTest extends BaseOsduFilter {
    private static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";
    private static final String COLLABORATION_DIRECTIVES = "id=8e1c4e74-3b9b-4b17-a0d5-67766558ec65,application=Unit test";
    private static final String FEATURE_NAME = "collaborations-enabled";
    @Mock
    public IFeatureFlag iCollaborationFeatureFlag;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private PrintWriter writer;
    @Mock
    private FilterChain filterChain;
    @InjectMocks
    private CollaborationFilter collaborationFilter;

    @Before
    public void setup() {
        ReflectionTestUtils.setField(collaborationFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));
        initMocks(this);
    }

    @Test
    public void shouldThrowException_ifCollaborationHeaderProvided_whenCollaborationFeatureFlagDisabled() throws IOException, ServletException {
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        when(iCollaborationFeatureFlag.isFeatureEnabled(FEATURE_NAME)).thenReturn(false);
        when(httpServletRequest.getHeader(X_COLLABORATION_HEADER_NAME)).thenReturn(COLLABORATION_DIRECTIVES);
        when(httpServletResponse.getWriter()).thenReturn(writer);

        try {
            collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        } catch (AppException e) {
            Assert.assertEquals(e.getError().getCode(), HttpStatus.SC_LOCKED);
            Assert.assertEquals(e.getError().getReason(), "Locked");
            Assert.assertEquals(e.getError().getMessage(), "Feature is not enabled on this environment");
        }
    }

    @Test
    public void shouldSkipFilter_ifUrlContainsHealthEndpoint() throws IOException, ServletException {
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/health");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(iCollaborationFeatureFlag, never()).isFeatureEnabled(FEATURE_NAME);
    }

    @Test
    public void shouldSkipFilter_ifUrlContainsInfoEndpoint() throws IOException, ServletException {
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/info");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(iCollaborationFeatureFlag, never()).isFeatureEnabled(FEATURE_NAME);
    }

    @Test
    public void shouldSkipFilter_ifUrlContainsSwaggerEndpoint() throws IOException, ServletException {
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/swagger-ui/index.html");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(iCollaborationFeatureFlag, never()).isFeatureEnabled(FEATURE_NAME);
    }

    @Test
    public void shouldSkipFilter_ifUrlContainsApiDocsEndpoint() throws IOException, ServletException {
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/v3/api-docs");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        verify(iCollaborationFeatureFlag, never()).isFeatureEnabled(FEATURE_NAME);
    }
}
