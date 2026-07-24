package com.colla.platform.modules.event.contract;

public abstract class DomainEventHandlingException extends RuntimeException {
    protected DomainEventHandlingException(String message) {
        super(message);
    }

    public static final class Permanent extends DomainEventHandlingException {
        public Permanent(String message) {
            super(message);
        }
    }

    public static final class Transient extends DomainEventHandlingException {
        public Transient(String message) {
            super(message);
        }
    }
}
