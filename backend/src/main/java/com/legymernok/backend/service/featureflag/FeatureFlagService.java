package com.legymernok.backend.service.featureflag;

import com.legymernok.backend.dto.featureflag.FeatureFlagResponse;
import com.legymernok.backend.dto.featureflag.UpdateFeatureFlagRequest;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.featureflag.FeatureFlag;
import com.legymernok.backend.repository.featureflag.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> getAllFlags() {
        return featureFlagRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse getFlagByKey(String key) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", "key", key));
        return mapToResponse(flag);
    }

    @Transactional
    public FeatureFlagResponse updateFlag(String key, UpdateFeatureFlagRequest request) {
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", "key", key));

        flag.setEnabled(request.getEnabled());
        if (request.getDescription() != null) {
            flag.setDescription(request.getDescription());
        }

        FeatureFlag updatedFlag = featureFlagRepository.save(flag);
        log.info("Updated FeatureFlag: {} -> enabled={}", updatedFlag.getKey(), updatedFlag.isEnabled());
        return mapToResponse(updatedFlag);
    }

    private FeatureFlagResponse mapToResponse(FeatureFlag flag) {
        return FeatureFlagResponse.builder()
                .id(flag.getId())
                .key(flag.getKey())
                .enabled(flag.isEnabled())
                .description(flag.getDescription())
                .createdAt(flag.getCreatedAt())
                .updatedAt(flag.getUpdatedAt())
                .build();
    }
}
