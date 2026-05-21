package org.example.dndncore.redis.lock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RedisLockKeys {

    private static final DateTimeFormatter HOURLY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH");

    private RedisLockKeys() {
    }

    public static String esgDailyRollover(LocalDate reportDate) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now();
        return "lock:esg:daily-rollover:" + targetDate;
    }

    public static String weatherStartupWarmup(LocalDate reportDate) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now();
        return "lock:weather:startup-warmup:" + targetDate;
    }

    public static String weatherHourlyRefresh(LocalDateTime refreshTime) {
        LocalDateTime targetTime = refreshTime != null ? refreshTime : LocalDateTime.now();
        return "lock:weather:snapshot-refresh:" + targetTime.format(HOURLY_TIME_FORMATTER);
    }
}
