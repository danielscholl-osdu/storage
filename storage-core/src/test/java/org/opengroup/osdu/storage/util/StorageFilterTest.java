package org.opengroup.osdu.storage.util;

import ch.qos.logback.core.Appender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.slf4j.LoggerFactory;

import org.opengroup.osdu.core.common.model.http.AppError;
import org.opengroup.osdu.core.common.model.http.AppException;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class StorageFilterTest extends BaseOsduFilter{

    @Mock
    private DpsHeaders dpsHeaders;

    @InjectMocks
    private StorageFilter storageFilter;
    @Mock
    private PrintWriter writer;   

    @Test
    public void shouldSetCorrectResponseHeaders() throws IOException, ServletException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("opendes");
        Mockito.when(dpsHeaders.getAuthorization()).thenReturn("token ");
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        ReflectionTestUtils.setField(storageFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");

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
    public void shouldReturnErrorWhenMissingDataPartitionIdHeader() throws IOException, ServletException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(httpServletResponse.getWriter()).thenReturn(writer);
        Mockito.when(dpsHeaders.getAuthorization()).thenReturn("token");
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        ReflectionTestUtils.setField(storageFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));
        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        AppError error = new AppError(HttpStatus.SC_BAD_REQUEST, "Bad Request", "data-partition-id header is missing");
        Mockito.verify(writer).write(appErrorToJson(error));
    }

    @Test
    public void shouldThrowExceptionWhenMissingAuthHeader() throws IOException, ServletException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(httpServletResponse.getWriter()).thenReturn(writer);
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        ReflectionTestUtils.setField(storageFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));
        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        AppError error = new AppError(HttpStatus.SC_UNAUTHORIZED, "Unauthorized", "Authorization header is missing");
        Mockito.verify(writer).write(appErrorToJson(error));
    }

    @Test
    public void shouldCatchAppExceptionsThrownFromDoFilter() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(httpServletResponse.getWriter()).thenReturn(writer);
        org.springframework.test.util.ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        Mockito.when(dpsHeaders.getAuthorization()).thenReturn("token");
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("data-partition");
        ReflectionTestUtils.setField(storageFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));

        doThrow(new AppException(HttpStatus.SC_LOCKED, "Locked", "Feature is not enabled on this environment")).when(filterChain).doFilter(httpServletRequest, httpServletResponse);

        storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);
        AppError error = new AppError(HttpStatus.SC_LOCKED, "Locked", "Feature is not enabled on this environment");
        Mockito.verify(writer).write(appErrorToJson(error));
    }

    @Test
    public void shouldLogErrorAndRethrowThrowableThrownFromDoFilter() throws ServletException, IOException {
        HttpServletRequest httpServletRequest = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse httpServletResponse = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(dpsHeaders.getCorrelationId()).thenReturn("correlation-id-value");
        Mockito.when(httpServletRequest.getMethod()).thenReturn("POST");
        Mockito.when(dpsHeaders.getAuthorization()).thenReturn("token");
        Mockito.when(dpsHeaders.getPartitionId()).thenReturn("data-partition");
        when(httpServletRequest.getRequestURI()).thenReturn("https://my-service-url/api/storage/v2/");
        when(httpServletRequest.getContextPath()).thenReturn("/api/storage/v2/");
        ReflectionTestUtils.setField(storageFilter, "excludedPaths", Arrays.asList("info", "swagger", "health", "api-docs"));
        ReflectionTestUtils.setField(storageFilter, "ACCESS_CONTROL_ALLOW_ORIGIN_DOMAINS", "custom-domain");

        // attach mock appender to capture logs from StorageFilter's logger
        Logger logger = (Logger) LoggerFactory.getLogger(StorageFilter.class);
        Appender mockAppender = Mockito.mock(Appender.class);
        logger.addAppender(mockAppender);

        try {
            doThrow(new AssertionError("this should be caught as a throwable")).when(filterChain).doFilter(httpServletRequest, httpServletResponse);

            Error thrown = assertThrows(Error.class, () -> storageFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));

            // verify that the thrown error is the one we threw from the filter chain
            assertTrue(thrown instanceof AssertionError);
            assertEquals("this should be caught as a throwable", thrown.getMessage());

            // verify that that the logger received the expected log entry
            Mockito.verify(mockAppender, Mockito.atLeastOnce()).doAppend(Mockito.argThat(
            (ILoggingEvent event) ->
                event.getLevel() == Level.ERROR &&
                event.getMessage() != null &&
                event.getMessage().contains("Unhandled throwable caught in StorageFilter") &&
                event.getThrowableProxy().getClassName().equals(AssertionError.class.getName()) &&                    
                event.getThrowableProxy() != null &&
                event.getThrowableProxy().getMessage() != null &&
                event.getThrowableProxy().getMessage().contains("this should be caught as a throwable")
            ));
        } finally {
            // detach mock appender so it doesn't interfere with other tests
            logger.detachAppender(mockAppender);
        }
    }
}
