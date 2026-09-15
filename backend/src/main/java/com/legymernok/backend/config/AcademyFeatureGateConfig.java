package com.legymernok.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.security.AcademyFeatureGateFilter;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Önálló servlet-filterként, a Spring Security lánca ELÉ regisztrálja az
 * {@link AcademyFeatureGateFilter}-t. Szándékosan nem a {@code SecurityConfig}-ba van kötve:
 * a controller security-tesztek {@code @Import(SecurityConfig.class)}-szal töltik be a láncot,
 * és nem kell mindegyikbe a feature flag service-t bemockolni.
 */
@Configuration
public class AcademyFeatureGateConfig {

    static final int ORDER = SecurityProperties.DEFAULT_FILTER_ORDER - 1;

    @Bean
    public FilterRegistrationBean<AcademyFeatureGateFilter> academyFeatureGateFilter(
            FeatureFlagService featureFlagService, ObjectMapper objectMapper) {
        FilterRegistrationBean<AcademyFeatureGateFilter> registration =
                new FilterRegistrationBean<>(new AcademyFeatureGateFilter(featureFlagService, objectMapper));
        registration.addUrlPatterns("/api/academy", "/api/academy/*");
        registration.setOrder(ORDER);
        return registration;
    }
}
