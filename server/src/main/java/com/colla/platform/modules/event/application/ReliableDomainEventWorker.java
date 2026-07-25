package com.colla.platform.modules.event.application;

import com.colla.platform.config.runtime.ConditionalOnRuntimeRole;
import com.colla.platform.config.runtime.RuntimeRole;
import com.colla.platform.config.runtime.RuntimeRoleProperties;
import com.colla.platform.modules.event.contract.DomainEventHandler;
import com.colla.platform.modules.event.contract.DomainEventHandler.EventMessage;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.DeliveryBacklogStats;
import com.colla.platform.modules.event.domain.DomainEventDeliveryModels.EventDelivery;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRuntimeRole({RuntimeRole.WORKER, RuntimeRole.COMBINED})
@ConditionalOnProperty(prefix = "colla.events.worker", name = "enabled", havingValue = "true")
public class ReliableDomainEventWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(ReliableDomainEventWorker.class);

    private final DomainEventDeliveryCoordinator coordinator;
    private final DomainEventHandlerRegistry registry;
    private final DomainEventWorkerProperties properties;
    private final String workerId;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private final AtomicLong expired = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();
    private final AtomicLong deadLetters = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private volatile ThreadPoolExecutor executor;
    private volatile ScheduledExecutorService heartbeatExecutor;
    private volatile boolean running;
    private volatile boolean acceptingClaims;
    private volatile long lastSuccessfulPollNanos;
    private volatile String lastPollFailure;
    private volatile Instant lastRecovery = Instant.EPOCH;

    public ReliableDomainEventWorker(
        DomainEventDeliveryCoordinator coordinator,
        DomainEventHandlerRegistry registry,
        DomainEventWorkerProperties properties,
        DomainEventDeliveryProperties deliveryProperties,
        RuntimeRoleProperties runtimeProperties,
        MeterRegistry meterRegistry
    ) {
        this.coordinator = coordinator;
        this.registry = registry;
        this.properties = properties;
        this.workerId = runtimeProperties.getInstanceId();
        this.meterRegistry = meterRegistry;
        deliveryProperties.validate();
        properties.validate(deliveryProperties);
        registerGauges();
    }

    @Scheduled(fixedDelayString = "${colla.events.worker.poll-interval:1s}")
    public void scheduledPoll() {
        pollOnce();
    }

    void pollOnce() {
        if (!running || !acceptingClaims) {
            return;
        }
        ThreadPoolExecutor current = executor;
        int capacity = properties.capacity() - current.getActiveCount() - current.getQueue().size();
        if (capacity <= 0) {
            counter("colla.event.worker.backpressure", "none").increment();
            return;
        }
        try {
            Instant pollStartedAt = coordinator.currentTime();
            if (!pollStartedAt.isBefore(lastRecovery.plus(properties.getRecoveryInterval()))) {
                int recovered = coordinator.recoverExpired(pollStartedAt);
                if (recovered > 0) {
                    counter("colla.event.worker.recovered", "none").increment(recovered);
                }
                lastRecovery = coordinator.currentTime();
            }
            int limit = Math.min(Math.min(capacity, properties.getClaimBatch()), properties.capacity());
            Instant claimStartedAt = coordinator.currentTime();
            List<EventDelivery> deliveries = coordinator.claim(workerId, limit, claimStartedAt);
            for (EventDelivery delivery : deliveries) {
                submit(current, delivery);
            }
            Instant pollCompletedAt = coordinator.currentTime();
            updateBacklog(coordinator.stats(pollCompletedAt));
            lastSuccessfulPollNanos = System.nanoTime();
            lastPollFailure = null;
        } catch (RuntimeException exception) {
            lastPollFailure = exception.getClass().getSimpleName();
            counter("colla.event.worker.poll.failures", "none").increment();
            log.error("domain_event_worker_poll_failed workerId={} error={}", workerId, lastPollFailure, exception);
        }
    }

    private void submit(ThreadPoolExecutor current, EventDelivery delivery) {
        DeliveryTask task = new DeliveryTask(delivery);
        try {
            task.startHeartbeat();
            current.execute(task);
            counter("colla.event.worker.claimed", delivery.handlerKey()).increment();
        } catch (RejectedExecutionException exception) {
            task.cancelHeartbeat();
            coordinator.release(delivery, coordinator.currentTime(), "Worker queue rejected claimed delivery");
            counter("colla.event.worker.rejected", delivery.handlerKey()).increment();
        }
    }

    private void execute(EventDelivery delivery, ScheduledFuture<?> heartbeat) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "completed";
        try {
            DomainEventHandler handler;
            try {
                handler = registry.require(delivery.handlerKey(), delivery.handlerVersion());
            } catch (IllegalArgumentException exception) {
                throw new DomainEventPermanentFailureException(exception.getMessage());
            }
            handler.handle(toMessage(delivery));
            if (!coordinator.complete(delivery, Map.of("workerId", workerId), coordinator.currentTime()).accepted()) {
                outcome = "stale";
            }
        } catch (RuntimeException exception) {
            outcome = "failed";
            if (!acceptingClaims && Thread.currentThread().isInterrupted()) {
                coordinator.release(delivery, coordinator.currentTime(), "Worker stopped during delivery");
            } else {
                coordinator.fail(delivery, exception, coordinator.currentTime());
            }
            counter("colla.event.worker.failed", delivery.handlerKey()).increment();
        } finally {
            heartbeat.cancel(false);
            sample.stop(timer(delivery.handlerKey(), outcome));
            counter("colla.event.worker." + outcome, delivery.handlerKey()).increment();
        }
    }

    private EventMessage toMessage(EventDelivery delivery) {
        var event = delivery.event();
        return new EventMessage(
            event.id(), event.workspaceId(), event.eventType(), event.eventVersion(),
            event.aggregateType(), event.aggregateId(), event.aggregateSequence(), event.actorId(),
            event.idempotencyKey(), event.correlationId(), event.causationId(), event.occurredAt(), event.payload()
        );
    }

    private void updateBacklog(DeliveryBacklogStats stats) {
        pending.set(stats.pending());
        processing.set(stats.processing());
        expired.set(stats.expiredLeases());
        retries.set(stats.retries());
        deadLetters.set(stats.deadLetters());
        oldestAgeSeconds.set(stats.oldestPendingAgeSeconds());
    }

    private void registerGauges() {
        Gauge.builder("colla.event.worker.queue.depth", this, worker -> worker.queueDepth()).register(meterRegistry);
        Gauge.builder("colla.event.worker.active", this, worker -> worker.activeTasks()).register(meterRegistry);
        Gauge.builder("colla.event.delivery.pending", pending, AtomicLong::get).register(meterRegistry);
        Gauge.builder("colla.event.delivery.processing", processing, AtomicLong::get).register(meterRegistry);
        Gauge.builder("colla.event.delivery.expired", expired, AtomicLong::get).register(meterRegistry);
        Gauge.builder("colla.event.delivery.retries", retries, AtomicLong::get).register(meterRegistry);
        Gauge.builder("colla.event.delivery.dead.letter", deadLetters, AtomicLong::get).register(meterRegistry);
        Gauge.builder("colla.event.delivery.oldest.age.seconds", oldestAgeSeconds, AtomicLong::get).register(meterRegistry);
    }

    private Counter counter(String name, String handler) {
        return Counter.builder(name).tag("handler", handler).register(meterRegistry);
    }

    private Timer timer(String handler, String outcome) {
        return Timer.builder("colla.event.worker.processing")
            .tag("handler", handler)
            .tag("outcome", outcome)
            .publishPercentileHistogram()
            .register(meterRegistry);
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory taskFactory = runnable -> new Thread(runnable, "event-worker-" + workerId + "-task-" + threadNumber.incrementAndGet());
        BlockingQueue<Runnable> workQueue = properties.getQueueCapacity() == 0
            ? new SynchronousQueue<>()
            : new ArrayBlockingQueue<>(properties.getQueueCapacity());
        executor = new ThreadPoolExecutor(
            properties.getConcurrency(), properties.getConcurrency(), 0L, TimeUnit.MILLISECONDS,
            workQueue, taskFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
        executor.prestartAllCoreThreads();
        heartbeatExecutor = java.util.concurrent.Executors.newScheduledThreadPool(
            1,
            runnable -> new Thread(runnable, "event-worker-" + workerId + "-heartbeat")
        );
        acceptingClaims = true;
        running = true;
        log.info(
            "domain_event_worker_started workerId={} concurrency={} queueCapacity={} claimBatch={} connectionBudget={}",
            workerId, properties.getConcurrency(), properties.getQueueCapacity(),
            properties.getClaimBatch(), properties.getConnectionBudget()
        );
    }

    @Override
    public synchronized void stop() {
        stop(() -> { });
    }

    @Override
    public synchronized void stop(Runnable callback) {
        if (!running) {
            callback.run();
            return;
        }
        acceptingClaims = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(properties.getShutdownGrace().toMillis(), TimeUnit.MILLISECONDS)) {
                List<Runnable> queued = new ArrayList<>(executor.shutdownNow());
                for (Runnable runnable : queued) {
                    if (runnable instanceof DeliveryTask task) {
                        task.cancelHeartbeat();
                        coordinator.release(
                            task.delivery,
                            coordinator.currentTime(),
                            "Worker shutdown released queued delivery"
                        );
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        heartbeatExecutor.shutdownNow();
        running = false;
        callback.run();
        log.info("domain_event_worker_stopped workerId={}", workerId);
    }

    @PreDestroy
    void destroy() {
        stop();
    }

    boolean ready() {
        if (!properties.isEnabled()) {
            return true;
        }
        if (!running || !acceptingClaims || lastPollFailure != null) {
            return false;
        }
        long successfulPollNanos = lastSuccessfulPollNanos;
        return successfulPollNanos == 0L
            || System.nanoTime() - successfulPollNanos <= properties.getPollInterval().multipliedBy(5).toNanos();
    }

    String readinessDetail() {
        if (!properties.isEnabled()) return "disabled";
        if (!running) return "stopped";
        if (!acceptingClaims) return "draining";
        return lastPollFailure == null ? "ready" : "poll-failed:" + lastPollFailure;
    }

    int queueDepth() { return executor == null ? 0 : executor.getQueue().size(); }
    int activeTasks() { return executor == null ? 0 : executor.getActiveCount(); }
    boolean acceptingClaims() { return acceptingClaims; }

    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }

    private final class DeliveryTask implements Runnable {
        private final EventDelivery delivery;
        private volatile ScheduledFuture<?> heartbeat;
        private DeliveryTask(EventDelivery delivery) { this.delivery = delivery; }
        private void startHeartbeat() {
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> renewLease(delivery),
                properties.getHeartbeatInterval().toMillis(),
                properties.getHeartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
        private void cancelHeartbeat() {
            ScheduledFuture<?> current = heartbeat;
            if (current != null) current.cancel(false);
        }
        @Override public void run() { execute(delivery, heartbeat); }
    }

    private void renewLease(EventDelivery delivery) {
        try {
            if (!coordinator.heartbeat(delivery, coordinator.currentTime())) {
                counter("colla.event.worker.heartbeat.stale", delivery.handlerKey()).increment();
                log.warn(
                    "domain_event_delivery_heartbeat_stale deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={}",
                    delivery.id(), delivery.event().id(), delivery.handlerKey(), workerId, delivery.fencingToken()
                );
            }
        } catch (RuntimeException exception) {
            counter("colla.event.worker.heartbeat.failures", delivery.handlerKey()).increment();
            log.warn(
                "domain_event_delivery_heartbeat_failed deliveryId={} eventId={} handlerKey={} workerId={} fencingToken={} error={}",
                delivery.id(), delivery.event().id(), delivery.handlerKey(), workerId,
                delivery.fencingToken(), exception.getClass().getSimpleName()
            );
        }
    }
}
