package com.legymernok.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.security.AcademyFeatureGateFilter;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AcademyFeatureGateConfigTest {

    @Test
    void registration_shouldRunBeforeSecurityChainOnAllPaths() {
        FilterRegistrationBean<AcademyFeatureGateFilter> registration = new AcademyFeatureGateConfig()
                .academyFeatureGateFilter(mock(FeatureFlagService.class), new ObjectMapper());

        assertTrue(registration.getOrder() < SecurityProperties.DEFAULT_FILTER_ORDER);
        assertEquals(java.util.Set.of("/*"), new java.util.HashSet<>(registration.getUrlPatterns()));
    }
}
