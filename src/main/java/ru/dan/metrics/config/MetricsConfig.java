package ru.dan.metrics.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter eventsCounter(MeterRegistry registry) {
        return Counter.builder("ingest_events_total")
                .description("Total ingested IoT events")
                .register(registry);
    }
}