package com.erp.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String resource, Long id) {
        super(resource + " not found with id: " + id);
    }
}
