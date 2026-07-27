package com.legymernok.backend.dto.mission;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FilePathRequest {
    @NotBlank
    private String path;
}
