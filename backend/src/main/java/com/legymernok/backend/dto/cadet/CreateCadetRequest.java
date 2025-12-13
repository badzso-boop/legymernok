package com.legymernok.backend.dto.cadet;

import lombok.Data;

@Data
public class CreateCadetRequest {
    private String username;
    private String email;
    private String password;
}
