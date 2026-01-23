package com.legymernok.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_GATEWAY)
public class ExternalServiceException extends RuntimeException {
    private String serviceName;

    public ExternalServiceException(String serviceName, String message) {
        super(String.format("Error in %s: %s", serviceName, message));
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String message) {
        super(message);
    }

    public String getServiceName() {
        return serviceName;
    }
}