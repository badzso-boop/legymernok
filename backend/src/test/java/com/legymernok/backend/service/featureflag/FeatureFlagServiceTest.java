package com.legymernok.backend.service.featureflag;

import com.legymernok.backend.dto.featureflag.FeatureFlagResponse;
import com.legymernok.backend.dto.featureflag.UpdateFeatureFlagRequest;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.featureflag.FeatureFlag;
import com.legymernok.backend.repository.featureflag.FeatureFlagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagServiceTest {

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @InjectMocks
    private FeatureFlagService featureFlagService;

    @Test
    void getAllFlags_ReturnsAll() {
        FeatureFlag flag = FeatureFlag.builder()
                .id(UUID.randomUUID())
                .key("ai_chatbot")
                .enabled(false)
                .description("AI chatbot")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(featureFlagRepository.findAll()).thenReturn(List.of(flag));

        List<FeatureFlagResponse> responses = featureFlagService.getAllFlags();

        assertEquals(1, responses.size());
        assertEquals("ai_chatbot", responses.get(0).getKey());
        assertFalse(responses.get(0).isEnabled());
    }

    @Test
    void getFlagByKey_Success() {
        FeatureFlag flag = FeatureFlag.builder()
                .id(UUID.randomUUID())
                .key("ai_chatbot")
                .enabled(false)
                .build();

        when(featureFlagRepository.findByKey("ai_chatbot")).thenReturn(Optional.of(flag));

        FeatureFlagResponse response = featureFlagService.getFlagByKey("ai_chatbot");

        assertEquals("ai_chatbot", response.getKey());
        assertFalse(response.isEnabled());
    }

    @Test
    void getFlagByKey_NotFound_ThrowsException() {
        when(featureFlagRepository.findByKey("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> featureFlagService.getFlagByKey("unknown"));
    }

    @Test
    void updateFlag_Success() {
        FeatureFlag flag = FeatureFlag.builder()
                .id(UUID.randomUUID())
                .key("ai_chatbot")
                .enabled(false)
                .description("Old description")
                .build();

        UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest();
        request.setEnabled(true);
        request.setDescription("New description");

        when(featureFlagRepository.findByKey("ai_chatbot")).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(i -> i.getArgument(0));

        FeatureFlagResponse response = featureFlagService.updateFlag("ai_chatbot", request);

        assertTrue(response.isEnabled());
        assertEquals("New description", response.getDescription());
        verify(featureFlagRepository).save(flag);
    }

    @Test
    void updateFlag_NotFound_ThrowsException() {
        UpdateFeatureFlagRequest request = new UpdateFeatureFlagRequest();
        request.setEnabled(true);

        when(featureFlagRepository.findByKey("unknown")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> featureFlagService.updateFlag("unknown", request));
        verify(featureFlagRepository, never()).save(any());
    }
}
