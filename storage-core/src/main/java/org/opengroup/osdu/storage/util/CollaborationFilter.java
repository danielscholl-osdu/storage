package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.storage.response.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@Component
public class CollaborationFilter implements Filter {
    private static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";

    @Value("${collaboration.enabled:false}")
    private boolean isCollaborationEnabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!isCollaborationEnabled) {
            String collaborationHeader = ((HttpServletRequest) request).getHeader(X_COLLABORATION_HEADER_NAME);
            if (!Strings.isNullOrEmpty(collaborationHeader)) {
                httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                httpResponse.setStatus(HttpStatus.SC_LOCKED);
                ErrorResponse errorResponse = new ErrorResponse(HttpStatus.SC_LOCKED, "Locked","Feature is not enabled on this environment");
                PrintWriter writer = httpResponse.getWriter();
                writer.write(errorResponse.toString());
                writer.flush();
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
