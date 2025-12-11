package ru.dan.metrics.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.dan.metrics.model.Event;
import ru.dan.metrics.service.EventProcessor;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/event")
public class EventController {

    private final EventProcessor processor;

    public EventController(EventProcessor processor) {
        this.processor = processor;
    }

    @PostMapping
    public ResponseEntity<?> receiveEvent(@RequestBody Event event) {

        if (event.getDeviceId() == null || event.getDeviceId().isBlank()) {
            return ResponseEntity.badRequest().body("deviceId is required");
        }

        // если устройство не прислало timestamp — ставим текущий
        if (event.getTimestamp() == 0) {
            event.setTimestamp(Instant.now().toEpochMilli());
        }

        log.info(
                "Received event: deviceId={}, temp={}, hum={}, ts={}",
                event.getDeviceId(),
                event.getTemperature(),
                event.getHumidity(),
                event.getTimestamp()
        );

        processor.processEvent(event);

        return ResponseEntity.ok("accepted");
    }
}