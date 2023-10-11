package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.feature.IFeatureFlag;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static org.opengroup.osdu.storage.util.RecordConstants.COLLABORATIONS_FEATURE_NAME;

@Order(9)
@Component
public class CollaborationFilter extends BaseOsduFilter implements Filter {
    public static final String X_COLLABORATION_HEADER_NAME = "x-collaboration";

    @Autowired
    public IFeatureFlag collaborationFeatureFlag;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!isExcludedPath(httpRequest) && !collaborationFeatureFlag.isFeatureEnabled(COLLABORATIONS_FEATURE_NAME)) {
            String collaborationHeader = httpRequest.getHeader(X_COLLABORATION_HEADER_NAME);
            if (!Strings.isNullOrEmpty(collaborationHeader)) {
                //this exception will be caught and handled from the storage filter
                //that has the higher filter order
                throw new AppException(HttpStatus.SC_LOCKED, "Locked", "Feature is not enabled on this environment");
            }
        }
        chain.doFilter(request, response);

    }
}
