package ru.dan.metrics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import ru.dan.metrics.model.Event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProcessor {

    private static final int WINDOW_SIZE = 50;

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry registry;

    private final Counter eventsCounter;
    private final Counter anomaliesCounter;
    private final Timer processingTimer;

    // Фильтр для deviceId, чтобы gauge можно было обновлять
    private final Map<String, AtomicReference<Double>> rollingAvgGauges = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> zscoreGauges = new ConcurrentHashMap<>();

    @Autowired
    public EventProcessor(RedisTemplate<String, String> redisTemplate, MeterRegistry registry) {
        this.redisTemplate = redisTemplate;
        this.registry = registry;

        this.eventsCounter = registry.counter("iot_events_total");
        this.anomaliesCounter = registry.counter("iot_anomalies_total");
        this.processingTimer = registry.timer("iot_processing_time");
    }


    /** Создаём gauge на метрику (один раз на комбинацию deviceId + metric). */
    private AtomicReference<Double> initGauge(Map<String, AtomicReference<Double>> map,
                                              String name,
                                              String deviceId,
                                              String metricKey) {

        return map.computeIfAbsent(deviceId + "_" + metricKey, key -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);

            Gauge.builder(name, ref, AtomicReference::get)
                    .tag("deviceId", deviceId)
                    .register(registry);

            return ref;
        });
    }


    public void processEvent(Event event) {

        processingTimer.record(() -> {
            eventsCounter.increment();

            log.info("Received event: deviceId={}, ts={}, temp={}, hum={}",
                    event.getDeviceId(), event.getTimestamp(),
                    event.getTemperature(), event.getHumidity());

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

        // Rolling average + variance
        double sum = 0;
        double sumSq = 0;
        for (String s : raw) {
            double v = Double.parseDouble(s);
            sum += v;
            sumSq += v * v;
        }

        int n = raw.size();
        double avg = sum / n;
        double variance = (sumSq / n) - (avg * avg);
        double std = variance <= 0 ? 0 : Math.sqrt(variance);

        double z = std == 0 ? 0 : (value - avg) / std;
        if (Math.abs(z) > 3) {
            anomaliesCounter.increment();
        }

        // ------------------------
        // Обновляем Gauge-метрики
        // ------------------------
        AtomicReference<Double> avgGauge =
                initGauge(rollingAvgGauges, "iot_" + metric + "_rolling_avg", deviceId, metric);

        AtomicReference<Double> zGauge =
                initGauge(zscoreGauges, "iot_" + metric + "_zscore", deviceId, metric);

        avgGauge.set(avg);
        zGauge.set(z);

        // ------------------------
        // Сохраняем агрегаты в Redis
        // ------------------------
        String statKey = "device:" + deviceId + ":stat:" + metric;
        redisTemplate.opsForHash().put(statKey, "avg", String.valueOf(avg));
        redisTemplate.opsForHash().put(statKey, "zscore", String.valueOf(z));
        redisTemplate.opsForHash().put(statKey, "timestamp", String.valueOf(ts));
    }
}