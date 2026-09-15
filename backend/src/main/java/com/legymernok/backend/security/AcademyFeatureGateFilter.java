package com.legymernok.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Navigátor Akadémia végpontjait (`/api/academy/**`) a `navigator_academy` feature flag mögé
 * zárja: kikapcsolt flagnél 404-et ad, még az autentikáció előtt, így a végpontok létezése sem
 * derül ki. Regisztráció és sorrend: {@link com.legymernok.backend.config.AcademyFeatureGateConfig}.
 */
@RequiredArgsConstructor
public class AcademyFeatureGateFilter extends OncePerRequestFilter {

    public static final String FLAG_KEY = "navigator_academy";
    public static final String PATH_PREFIX = "/api/academy/";

    private final FeatureFlagService featureFlagService;
    private final ObjectMapper objectMapper;

    /**
     * A Spring MVC-vel azonos normalizálást használja (`;param` levágása, dekódolás, `//` összevonás),
     * különben pl. a `/api/academy;x=1/units` kérés elkerülné a kaput, a dispatch viszont
     * `/api/academy/units`-ra routolna. A security lánc `StrictHttpFirewall`-ja ezt ma elutasítaná,
     * de ez a filter azt megelőzi, ezért nem támaszkodhat rá.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return !(path.startsWith(PATH_PREFIX) || path.equals("/api/academy"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (featureFlagService.isEnabled(FLAG_KEY)) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("error", "Not Found");
        body.put("message", "A kért erőforrás nem található.");

        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
