package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.http.AppError;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(MockitoJUnitRunner.class)
public class CollaborationFilterTest {
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

    @InjectMocks
    private CollaborationFilter collaborationFilter;

    @Before
    public void setup() {
        initMocks(this);

        org.springframework.test.util.ReflectionTestUtils.setField(collaborationFilter, "isCollaborationEnabled", false);
    }
    
    @Test
    public void shouldThrowException_ifCollaborationHeaderProvided_whenCollaborationFeatureFlagDisabled() throws IOException, ServletException {
        Mockito.when(httpServletRequest.getHeader(X_COLLABORATION_HEADER_NAME)).thenReturn(COLLABORATION_DIRECTIVES);
        Mockito.when(httpServletResponse.getWriter()).thenReturn(writer);

        collaborationFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        Mockito.verify(httpServletResponse).setContentType("application/json");
        Mockito.verify(httpServletResponse).setStatus(HttpStatus.SC_LOCKED);
        AppError errorResponse = new AppError(HttpStatus.SC_LOCKED, "Locked","Feature is not enabled on this environment");
        Mockito.verify(writer).write(CollaborationFilter.appErrorToJson(errorResponse));
    }
}
