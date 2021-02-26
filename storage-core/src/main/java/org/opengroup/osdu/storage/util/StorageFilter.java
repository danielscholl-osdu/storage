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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.base.Strings;
import org.apache.http.HttpStatus;
import org.opengroup.osdu.core.common.http.ResponseHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;

@Component
public class StorageFilter implements Filter {

	private static final String DISABLE_AUTH_PROPERTY = "org.opengroup.osdu.storage.disableAuth";
	private static final String OPTIONS_STRING = "OPTIONS";
	private static final String FOR_HEADER_NAME = "frame-of-reference";


	@Autowired
	private DpsHeaders dpsHeaders;

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

		chain.doFilter(request, response);

		HttpServletResponse httpResponse = (HttpServletResponse) response;

		this.dpsHeaders.addCorrelationIdIfMissing();

		Map<String, List<Object>> standardHeaders = ResponseHeaders.STANDARD_RESPONSE_HEADERS;
		for (Map.Entry<String, List<Object>> header : standardHeaders.entrySet()) {
			httpResponse.addHeader(header.getKey(), header.getValue().toString());
		}
		httpResponse.addHeader(DpsHeaders.CORRELATION_ID, this.dpsHeaders.getCorrelationId());

		// This block handles the OPTIONS preflight requests performed by Swagger. We
		// are also enforcing requests coming from other origins to be rejected.
		if (httpRequest.getMethod().equalsIgnoreCase(OPTIONS_STRING)) {
			httpResponse.setStatus(HttpStatus.SC_OK);
		}
	}

	@Override
	public void destroy() {
	}
}