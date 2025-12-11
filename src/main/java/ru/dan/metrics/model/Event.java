package ru.dan.metrics.model;

import lombok.Data;

@Data
public class Event {

    /**
     * ID датчика.
     */
    private String deviceId;

    /**
     * Температура.
     */
    private double temperature;

    /**
     * Влажность.
     */
    private double humidity;

    /**
     * Время.
     */
    private long timestamp;
}
