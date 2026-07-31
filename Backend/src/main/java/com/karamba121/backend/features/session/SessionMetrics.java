package com.karamba121.backend.features.session;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class SessionMetrics {

    public static final String FAMILY_CREATED = "family_created";
    public static final String ROTATED = "rotated";
    public static final String REVOKED = "revoked";
    public static final String REPLAY_DETECTED = "replay_detected";

    public static final String SUCCESS = "success";
    public static final String REJECTED = "rejected";
    public static final String UNAVAILABLE = "unavailable";
    public static final String ERROR = "error";

    private static final String EVENT_METRIC = "identity_hub.session.refresh.events";
    private static final String DURATION_METRIC = "identity_hub.session.refresh.duration";

    private final Map<String, Counter> events;
    private final Map<String, Timer> durations;

    public SessionMetrics(MeterRegistry registry) {
        this.events = Map.of(
                FAMILY_CREATED, eventCounter(registry, FAMILY_CREATED),
                ROTATED, eventCounter(registry, ROTATED),
                REVOKED, eventCounter(registry, REVOKED),
                REPLAY_DETECTED, eventCounter(registry, REPLAY_DETECTED));
        this.durations = Map.of(
                SUCCESS, durationTimer(registry, SUCCESS),
                REJECTED, durationTimer(registry, REJECTED),
                UNAVAILABLE, durationTimer(registry, UNAVAILABLE),
                ERROR, durationTimer(registry, ERROR));
    }

    public void recordEventAfterCommit(String event) {
        Counter counter = required(events, event);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }

    public void recordRefreshAttempt(String outcome, long elapsedNanoseconds) {
        required(durations, outcome).record(elapsedNanoseconds, TimeUnit.NANOSECONDS);
    }

    private static Counter eventCounter(MeterRegistry registry, String event) {
        return Counter.builder(EVENT_METRIC)
                .description("Eventos persistidos do ciclo de refresh token")
                .tag("event", event)
                .register(registry);
    }

    private static Timer durationTimer(MeterRegistry registry, String outcome) {
        return Timer.builder(DURATION_METRIC)
                .description("Duração das tentativas de refresh token")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static <T> T required(Map<String, T> values, String key) {
        T value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Dimensão de métrica não permitida: " + key);
        }
        return value;
    }
}
