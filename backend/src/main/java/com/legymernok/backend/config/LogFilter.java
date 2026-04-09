package com.legymernok.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class LogFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Cache the request/response body so we can read it (otherwise the stream would close)
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 10);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Logging
            String requestBody = new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            // Only log the body if it's not too long and not a file upload
            if (requestBody.length() > 1000 || request.getContentType() != null && request.getContentType().contains("multipart")) {
                requestBody = "[Body too large or binary]";
            }
            // Filter out password (primitive solution, but protects against accidental logging)
            if (requestBody.contains("\"password\"")) {
                requestBody = "[HIDDEN SENSITIVE DATA]";
            }

            log.info("HTTP {} {} | Status: {} | Time: {}ms | Body: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    requestBody.isEmpty() ? "[Empty]" : requestBody);

            // Important: the response must be copied back to the original response!
            responseWrapper.copyBodyToResponse();
        }
    }
}
