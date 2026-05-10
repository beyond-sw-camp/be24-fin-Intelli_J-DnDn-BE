package org.example.dndn.weather.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.dndn.weather.WeatherInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class WeatherSnapshotScheduler {

    private final WeatherInfoService weatherInfoService;

    @Value("${weather.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${weather.scheduler.warmup-on-startup:true}")
    private boolean warmupOnStartup;

    // 애플리케이션 기동 직후 워밍업 (3일치)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || !warmupOnStartup) {
            return;
        }

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            try {
                weatherInfoService.refreshSnapshot(today.plusDays(i));
            } catch (Exception ignored) {
            }
        }
    }

    // 30분 주기 갱신
    @Scheduled(cron = "${weather.scheduler.cron:0 0/30 * * * *}")
    public void refreshUpcomingDays() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 7; i++) {
            try {
                weatherInfoService.refreshSnapshot(today.plusDays(i));
            } catch (Exception ignored) {
            }
<<<<<<< Updated upstream
=======

            cursor = cursor.plusDays(1);
>>>>>>> Stashed changes
        }
    }
}
