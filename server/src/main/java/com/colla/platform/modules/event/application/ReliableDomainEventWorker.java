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
    private volatile Instant lastSuccessfulPoll;
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
        Instant now = Instant.now();
        try {
            if (!now.isBefore(lastRecovery.plus(properties.getRecoveryInterval()))) {
                int recovered = coordinator.recoverExpired(now);
                if (recovered > 0) {
                    counter("colla.event.worker.recovered", "none").increment(recovered);
                }
                lastRecovery = now;
            }
            int limit = Math.min(Math.min(capacity, properties.getClaimBatch()), properties.capacity());
            List<EventDelivery> deliveries = coordinator.claim(workerId, limit, now);
            for (EventDelivery delivery : deliveries) {
                submit(current, delivery);
            }
            updateBacklog(coordinator.stats(now));
            lastSuccessfulPoll = now;
            lastPollFailure = null;
        } catch (RuntimeException exception) {
            lastPollFailure = exception.getClass().getSimpleName();
            counter("colla.event.worker.poll.failures", "none").increment();
            log.error("domain_event_worker_poll_failed workerId={} error={}", workerId, lastPollFailure, exception);
        }
    }

    private void submit(ThreadPoolExecutor current, EventDelivery delivery) {
        try {
            current.execute(new DeliveryTask(delivery));
            counter("colla.event.worker.claimed", delivery.handlerKey()).increment();
        } catch (RejectedExecutionException exception) {
            coordinator.release(delivery, Instant.now(), "Worker queue rejected claimed delivery");
            counter("colla.event.worker.rejected", delivery.handlerKey()).increment();
        }
    }

    private void execute(EventDelivery delivery) {
        Timer.Sample sample = Timer.start(meterRegistry);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
            () -> coordinator.heartbeat(delivery, Instant.now()),
            properties.getHeartbeatInterval().toMillis(),
            properties.getHeartbeatInterval().toMillis(),
            TimeUnit.MILLISECONDS
        );
        String outcome = "completed";
        try {
            DomainEventHandler handler;
            try {
                handler = registry.require(delivery.handlerKey(), delivery.handlerVersion());
            } catch (IllegalArgumentException exception) {
                throw new DomainEventPermanentFailureException(exception.getMessage());
            }
            handler.handle(toMessage(delivery));
            if (!coordinator.complete(delivery, Map.of("workerId", workerId), Instant.now()).accepted()) {
                outcome = "stale";
            }
        } catch (RuntimeException exception) {
            outcome = "failed";
            if (!acceptingClaims && Thread.currentThread().isInterrupted()) {
                coordinator.release(delivery, Instant.now(), "Worker stopped during delivery");
            } else {
                coordinator.fail(delivery, exception, Instant.now());
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
            Math.min(2, properties.getConcurrency()),
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
                        coordinator.release(task.delivery, Instant.now(), "Worker shutdown released queued delivery");
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
        return lastSuccessfulPoll == null
            || Duration.between(lastSuccessfulPoll, Instant.now()).compareTo(properties.getPollInterval().multipliedBy(5)) <= 0;
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
        private DeliveryTask(EventDelivery delivery) { this.delivery = delivery; }
        @Override public void run() { execute(delivery); }
    }
}
