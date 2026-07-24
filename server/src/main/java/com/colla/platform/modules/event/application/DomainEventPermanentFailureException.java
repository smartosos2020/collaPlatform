package com.colla.platform.modules.event.application;

public class DomainEventPermanentFailureException extends RuntimeException {
    public DomainEventPermanentFailureException(String message) {
        super(message);
    }
}
