package com.legymernok.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Üzleti szabály miatt elutasított kérés (400). Az üzenet a kliensnek szól, ne tartalmazzon belső részletet. */
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
