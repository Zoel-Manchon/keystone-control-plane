package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.infrastructure.events.FleetEventBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live event stream consumed by the console. Requires an authenticated operator. */
@RestController
public class FleetEventStreamController {

    private final FleetEventBroadcaster broadcaster;

    FleetEventStreamController(FleetEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return broadcaster.subscribe();
    }
}
