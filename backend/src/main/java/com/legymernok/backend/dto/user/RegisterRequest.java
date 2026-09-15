package com.legymernok.backend.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.legymernok.backend.model.audience.Audience;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.Year;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String fullName;
    private String password;

    /** Melyik belépési ponton regisztrált; hiányzó érték esetén ENGINEER (a meglévő kliensek így működnek tovább). */
    private Audience audience;

    @Min(1900)
    @Max(2100)
    private Short birthYear;

    @JsonIgnore
    @AssertTrue(message = "Az akadémiás regisztrációhoz a születési év kötelező.")
    public boolean isBirthYearProvidedForAcademy() {
        return audience != Audience.ACADEMY || birthYear != null;
    }

    @JsonIgnore
    @AssertTrue(message = "A születési év nem lehet a jövőben.")
    public boolean isBirthYearNotInFuture() {
        return birthYear == null || birthYear <= Year.now().getValue();
    }
}
