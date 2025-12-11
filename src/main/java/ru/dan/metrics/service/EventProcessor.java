package ru.dan.metrics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.dan.metrics.model.Event;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventProcessor {

    private static final int WINDOW_SIZE = 50;

    private final RedisTemplate<String, String> redisTemplate;

    private final MeterRegistry registry;

    private final Counter eventsCounter;
    private final Counter anomaliesCounter;
    private final Timer processingTimer;

    @Autowired
    public EventProcessor(RedisTemplate<String, String> redisTemplate, MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;

        this.eventsCounter = registry.counter("iot_events_total");
        this.anomaliesCounter = registry.counter("iot_anomalies_total");
        this.processingTimer = registry.timer("iot_processing_time");
    }

    public void processEvent(Event event) {
        processingTimer.record(() -> {

            eventsCounter.increment();

            processMetric(event.getDeviceId(), "temp", event.getTemperature(), event.getTimestamp());
            processMetric(event.getDeviceId(), "hum", event.getHumidity(), event.getTimestamp());
        });
    }

    private void processMetric(String deviceId, String metric, double value, long ts) {

        String windowKey = "device:" + deviceId + ":" + metric + ":window";

        redisTemplate.opsForList().leftPush(windowKey, String.valueOf(value));
        redisTemplate.opsForList().trim(windowKey, 0, WINDOW_SIZE - 1);

        List<String> raw = redisTemplate.opsForList().range(windowKey, 1, WINDOW_SIZE);

        if (raw == null || raw.size() < 10) {
            return;
        }

        // Rolling average & z-score
        double sum = 0;
        double sumSq = 0;
        for (String s : raw) {
            double v = Double.parseDouble(s);
            sum += v;
            sumSq += (v * v);
        }

        int count = raw.size();
        double mean = sum / count;
        double variance = (sumSq / count) - (mean * mean);
        double stddev = variance <= 0 ? 0 : Math.sqrt(variance);

        double z = stddev == 0 ? 0 : (value - mean) / stddev;

        if (Math.abs(z) > 3) {
            anomaliesCounter.increment();
        }

        // сохраняем агрегаты
        String statKey = "device:" + deviceId + ":stat:" + metric;

        redisTemplate.opsForHash().put(statKey, "avg", String.valueOf(mean));
        redisTemplate.opsForHash().put(statKey, "zscore", String.valueOf(z));
        redisTemplate.opsForHash().put(statKey, "timestamp", String.valueOf(ts));

        // Prometheus gauge
        registry.gauge("iot_" + metric + "_rolling_avg", mean);
        registry.gauge("iot_" + metric + "_zscore", z);
    }
}