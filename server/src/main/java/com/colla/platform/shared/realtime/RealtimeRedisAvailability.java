package com.colla.platform.shared.realtime;

import java.util.concurrent.atomic.AtomicBoolean;

public class RealtimeRedisAvailability {
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final Runnable unavailableAction;

    public RealtimeRedisAvailability(Runnable unavailableAction) {
        this.unavailableAction = unavailableAction;
    }

    public boolean isAvailable() {
        return available.get();
    }

    public boolean markAvailable() {
        return available.compareAndSet(false, true);
    }

    public boolean markUnavailable() {
        if (!available.compareAndSet(true, false)) {
            return false;
        }
        unavailableAction.run();
        return true;
    }
}
