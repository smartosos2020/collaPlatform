package com.colla.platform.modules.event.application;

public class DomainEventTransientFailureException extends RuntimeException {
    public DomainEventTransientFailureException(String message) {
        super(message);
    }
}
