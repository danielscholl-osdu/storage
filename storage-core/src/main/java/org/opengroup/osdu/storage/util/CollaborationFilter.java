package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.springframework.beans.factory.annotation.Autowired;
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

import static org.opengroup.osdu.storage.util.StringConstants.COLLABORATIONS_FEATURE_NAME;

@Component
public class CollaborationFilter implements Filter {
    public static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";

    @Autowired
    public IFeatureFlag collaborationFeatureFlag;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if(httpRequest.getRequestURI().contains("info") ||
                httpRequest.getRequestURI().contains("swagger") ||
                httpRequest.getRequestURI().contains("health") ||
                httpRequest.getRequestURI().contains("api-docs"))
            return;

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            String collaborationHeader = httpRequest.getHeader(X_COLLABORATION_HEADER_NAME);
            if (!Strings.isNullOrEmpty(collaborationHeader)) {
                httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                httpResponse.setStatus(HttpStatus.SC_LOCKED);
                AppError errorResponse = new AppError(HttpStatus.SC_LOCKED, "Locked", "Feature is not enabled on this environment");
                PrintWriter writer = httpResponse.getWriter();
                writer.write(appErrorToJson(errorResponse));
                writer.flush();
                return;
            }
        }

        chain.doFilter(request, response);
    }

    public static String appErrorToJson(AppError appError) {
        return "{\"code\": " + appError.getCode() + ",\"reason\": \"" + appError.getReason() + "\",\"message\": \"" + appError.getMessage() + "\"}";
    }

}
