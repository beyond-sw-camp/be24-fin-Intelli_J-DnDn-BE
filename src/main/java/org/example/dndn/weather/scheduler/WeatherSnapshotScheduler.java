package org.example.dndn.weather.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.dndn.weather.WeatherInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 기상 스냅샷 주기 갱신 스케줄러.
 * - 애플리케이션 기동 직후: 오늘/내일/모레 3일치 워밍업 실행
 * - cron 주기: 오늘부터 7일치 갱신 (월간 표출용 데이터까지 미리 적재)
 *   사용자는 어느 시점에 들어와도 캐시된 최신 데이터를 즉시 받게 됨
 */
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

    // 30분 주기 갱신 (yml 의 weather.scheduler.cron 기준)
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
        }
    }
}
