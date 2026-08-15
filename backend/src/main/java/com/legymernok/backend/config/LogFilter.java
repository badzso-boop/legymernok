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

        // Cache-eljük a request/response body-t, hogy ki tudjuk olvasni (különben a stream bezáródna)
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 10);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Logolás
            String requestBody = new String(requestWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            // Csak akkor logoljuk a body-t, ha nem túl hosszú és nem fájlfeltöltés
            if (requestBody.length() > 1000 || request.getContentType() != null && request.getContentType().contains("multipart")) {
                requestBody = "[Body too large or binary]";
            }
            // Password kiszűrése (primitív megoldás, de véd a véletlen logolástól)
            if (requestBody.contains("\"password\"")) {
                requestBody = "[HIDDEN SENSITIVE DATA]";
            }

            log.info("HTTP {} {} | Status: {} | Time: {}ms | Body: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    requestBody.isEmpty() ? "[Empty]" : requestBody);

            // Fontos: a választ vissza kell másolni az eredeti response-ba!
            responseWrapper.copyBodyToResponse();
        }
    }
}
