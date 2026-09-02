package dev.zoel.keystone.infrastructure.events;

import dev.zoel.keystone.application.port.out.DeviceEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans fleet events out to every console currently watching, over Server-Sent Events.
 *
 * SSE rather than WebSocket because the traffic is one-way: the server pushes, the
 * console listens. SSE is plain HTTP, reconnects on its own, and needs no extra
 * protocol handling - a WebSocket here would be complexity without a payoff.
 *
 * CopyOnWriteArrayList because reads (broadcasting) vastly outnumber writes
 * (a console connecting or dropping).
 */
@Component
public class FleetEventBroadcaster implements DeviceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(FleetEventBroadcaster.class);
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        return emitter;
    }

    @Override
    public void publish(FleetEvent event) {
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                    .name("fleet")
                    .data(new PublishedEvent(
                        event.occurredAt().toString(),
                        event.action().name(),
                        event.subject(),
                        event.detail())));
            } catch (IOException | IllegalStateException e) {
                // A dropped console must never break the operation that emitted the
                // event, so failures are logged and the subscriber is discarded.
                subscribers.remove(emitter);
                log.debug("dropped an SSE subscriber: {}", e.getMessage());
            }
        }
    }

    public int subscriberCount() {
        return subscribers.size();
    }

    /** Wire format. Kept separate from the domain event so the two can evolve apart. */
    public record PublishedEvent(String occurredAt, String action, String subject, String detail) {}
}
