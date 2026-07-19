package com.legymernok.backend.dto.chat;

import java.util.Map;

public record ChatAction(String type, Map<String, String> fields) {}
