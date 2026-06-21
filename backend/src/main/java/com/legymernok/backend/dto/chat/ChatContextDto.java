package com.legymernok.backend.dto.chat;

import java.util.Map;

public record ChatContextDto(
        String currentPage,
        String pageType,
        Map<String, String> formFields,
        String language
) {}
