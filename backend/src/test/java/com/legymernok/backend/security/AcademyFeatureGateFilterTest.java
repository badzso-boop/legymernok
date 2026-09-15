package com.legymernok.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademyFeatureGateFilterTest {

    @Mock private FeatureFlagService featureFlagService;
    @Mock private FilterChain filterChain;

    private AcademyFeatureGateFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AcademyFeatureGateFilter(featureFlagService, new ObjectMapper());
    }

    @Test
    void academyPath_whenFlagDisabled_shouldReturn404AndStopChain() throws Exception {
        when(featureFlagService.isEnabled(AcademyFeatureGateFilter.FLAG_KEY)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/academy/units/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"status\":404"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void academyRootPath_whenFlagDisabled_shouldReturn404() throws Exception {
        when(featureFlagService.isEnabled(AcademyFeatureGateFilter.FLAG_KEY)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/academy");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(404, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void academyPath_whenFlagEnabled_shouldContinueChain() throws Exception {
        when(featureFlagService.isEnabled(AcademyFeatureGateFilter.FLAG_KEY)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/academy/attempts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void nonAcademyPath_shouldNotCheckFlag() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void similarPrefixPath_shouldNotBeGated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/academyx/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(featureFlagService);
    }
}
