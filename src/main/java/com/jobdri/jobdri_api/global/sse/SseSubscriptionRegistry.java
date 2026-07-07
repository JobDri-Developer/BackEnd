package com.jobdri.jobdri_api.global.sse;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class SseSubscriptionRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;
    private final ScheduledExecutorService heartbeatExecutor;

    public SseSubscriptionRegistry(
            @Value("${app.sse.timeout-millis:1800000}") long timeoutMillis,
            @Value("${app.sse.heartbeat-interval-millis:15000}") long heartbeatIntervalMillis
    ) {
        this.timeoutMillis = timeoutMillis;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        if (heartbeatIntervalMillis > 0) {
            this.heartbeatExecutor.scheduleAtFixedRate(
                    this::sendHeartbeatSafely,
                    heartbeatIntervalMillis,
                    heartbeatIntervalMillis,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public SseEmitter subscribe(String channelKey, String eventName, Object initialPayload, boolean completeAfterInitial) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.computeIfAbsent(channelKey, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(channelKey, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(channelKey, emitter);
        });
        emitter.onError(ignored -> remove(channelKey, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("connected"));
            emitter.send(SseEmitter.event().name(eventName).data(initialPayload));
            if (completeAfterInitial) {
                emitter.complete();
                remove(channelKey, emitter);
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            remove(channelKey, emitter);
        }

        return emitter;
    }

    public void publish(String channelKey, String eventName, Object payload, boolean completeAfterPublish) {
        List<SseEmitter> subscribers = emitters.get(channelKey);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
                if (completeAfterPublish) {
                    emitter.complete();
                    remove(channelKey, emitter);
                }
            } catch (IOException e) {
                emitter.completeWithError(e);
                remove(channelKey, emitter);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void sendHeartbeatSafely() {
        try {
            sendHeartbeat();
        } catch (Exception ignored) {
            // Heartbeat failures should not affect the rest of the application.
        }
    }

    private void sendHeartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            String channelKey = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    remove(channelKey, emitter);
                }
            }
        }
    }

    private void remove(String channelKey, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(channelKey);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(channelKey);
        }
    }
}
