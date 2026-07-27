package com.legymernok.backend.dto.mission;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RenameFileRequest {
    @NotBlank
    private String oldPath;
    @NotBlank
    private String newPath;
}
