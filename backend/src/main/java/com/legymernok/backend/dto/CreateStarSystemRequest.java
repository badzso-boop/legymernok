package com.legymernok.backend.dto;

import lombok.Data;

@Data
public class CreateStarSystemRequest {
    private String name;
    private String description;
    private String iconUrl;
}