package com.legymernok.backend.dto.fillinblank;

import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class SubmitFillInBlankRequest {

    private Map<String, UUID> answers;
}
