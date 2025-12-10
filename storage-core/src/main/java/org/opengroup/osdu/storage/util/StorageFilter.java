// Copyright 2017-2019, Schlumberger
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.opengroup.osdu.storage.util;

import com.google.common.base.Strings;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpStatus;

import org.opengroup.osdu.core.common.http.ResponseHeadersFactory;
import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(8)
@Component
public class StorageFilter extends BaseOsduFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageFilter.class);

    private static final String DISABLE_AUTH_PROPERTY = "org.opengroup.osdu.storage.disableAuth";
    private static final String OPTIONS_STRING = "OPTIONS";
    private static final String FOR_HEADER_NAME = "frame-of-reference";
    // defaults to * for any front-end, string must be comma-delimited if more than one domain
    @Value("${ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS:*}")
    String ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS;

    private ResponseHeadersFactory responseHeadersFactory = new ResponseHeadersFactory();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        boolean disableAuth = Boolean.getBoolean(DISABLE_AUTH_PROPERTY);
        if (disableAuth) {
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String fetchConversionHeader = ((HttpServletRequest) request).getHeader(FOR_HEADER_NAME);
        if (!Strings.isNullOrEmpty(fetchConversionHeader)) {
            this.dpsHeaders.put(FOR_HEADER_NAME, fetchConversionHeader);
        }

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        this.dpsHeaders.addCorrelationIdIfMissing();

        Map<String, String> responseHeaders = responseHeadersFactory.getResponseHeaders(ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS);
        for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
            httpResponse.setHeader(header.getKey(), header.getValue());
        }
        httpResponse.setHeader(DpsHeaders.CORRELATION_ID, this.dpsHeaders.getCorrelationId());

        // This block handles the OPTIONS preflight requests performed by Swagger. We
        // are also enforcing requests coming from other origins to be rejected.
        if (httpRequest.getMethod().equalsIgnoreCase(OPTIONS_STRING)) {
            httpResponse.setStatus(HttpStatus.SC_OK);
        }

        try {
            if (!isExcludedPath(httpRequest)) {
                validateMandatoryHeaders();
            }
            chain.doFilter(request, response);
        } catch (AppException e) {
            //Unhandled Exceptions thrown by filters are not caught by the ExceptionManagers, instead spring security converts them to 500.
            // So, catch all the app exceptions(only, since we're throwing that when something bad happens) and convert them to json, instead of 500 if uncaught
            getErrorResponse(response, new AppError(e.getError().getCode(), e.getError().getReason(), e.getMessage()));
        } catch (Throwable t) {         
            this.LOGGER.error("Unhandled throwable caught in StorageFilter", t);
            throw t;
        }
    }


    @Override
    public void destroy() {
    }
}
