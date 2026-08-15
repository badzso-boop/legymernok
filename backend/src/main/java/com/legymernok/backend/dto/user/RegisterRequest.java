package com.legymernok.backend.dto.user;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String fullName;
    private String password;
}
