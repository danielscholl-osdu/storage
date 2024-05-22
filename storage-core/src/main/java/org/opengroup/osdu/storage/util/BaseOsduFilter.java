package org.opengroup.osdu.storage.util;

import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class BaseOsduFilter {
    @Autowired
    protected DpsHeaders dpsHeaders;

    @Value("#{'${collaborationFilter.excludedPaths:info,swagger,health,liveness_check,api-docs}'.split(',')}")
    private List<String> excludedPaths;

    protected static String appErrorToJson(AppError appError) {
        return "{\"code\": " + appError.getCode() + ",\"reason\": \"" + appError.getReason() + "\",\"message\": \"" + appError.getMessage() + "\"}";
    }

    protected static void getErrorResponse(ServletResponse response, AppError error) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        httpResponse.setStatus(error.getCode());
        PrintWriter writer = httpResponse.getWriter();
        writer.write(appErrorToJson(error));
        writer.flush();
    }

    protected void validateMandatoryHeaders() {
        if (this.dpsHeaders.getAuthorization() == null || this.dpsHeaders.getAuthorization().isEmpty()) {
            throw new AppException(HttpStatus.SC_UNAUTHORIZED, "Unauthorized", "Authorization header is missing");
        }
        if (this.dpsHeaders.getPartitionId() == null || this.dpsHeaders.getPartitionId().isEmpty()) {
            throw new AppException(HttpStatus.SC_BAD_REQUEST, "Bad Request", "data-partition-id header is missing");
        }
    }

    protected boolean isExcludedPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length() + 1);
        return excludedPaths.stream().anyMatch(path::contains);
    }
}
