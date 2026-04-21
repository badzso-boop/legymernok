package com.legymernok.backend.dto.mission;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ContentPageResponse {

    private UUID missionId;
    private String missionName;
    private String content;
    private int page;
    private int pageSize;
    private int totalLines;
    private int totalPages;
    private boolean hasNextPage;
    private boolean hasPreviousPage;
}