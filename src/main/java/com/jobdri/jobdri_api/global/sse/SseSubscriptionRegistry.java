package com.jobdri.jobdri_api.global.sse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseSubscriptionRegistry {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public SseSubscriptionRegistry(@Value("${app.sse.timeout-millis:1800000}") long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
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
