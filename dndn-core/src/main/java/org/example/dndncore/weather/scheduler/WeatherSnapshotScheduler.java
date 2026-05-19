package org.example.dndncore.weather.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.redis.lock.RedisDistributedLockExecutor;
import org.example.dndncore.redis.lock.RedisLockKeys;
import org.example.dndncore.weather.WeatherInfoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherSnapshotScheduler {

    private static final long LOCK_WAIT_TIME_SECONDS = 0L;
    private static final long LOCK_LEASE_TIME_SECONDS = 1_800L;

    private final WeatherInfoService weatherInfoService;
    private final RedisDistributedLockExecutor redisDistributedLockExecutor;

    @Value("${weather.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${weather.scheduler.warmup-on-startup:true}")
    private boolean warmupOnStartup;

    @Value("${weather.scheduler.startup-past-days:15}")
    private int startupPastDays;

    @Value("${weather.scheduler.startup-future-days:15}")
    private int startupFutureDays;

    @Value("${weather.scheduler.refresh-future-days:15}")
    private int refreshFutureDays;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || !warmupOnStartup) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(startupPastDays);
        LocalDate endDate = today.plusDays(startupFutureDays);
        String lockKey = RedisLockKeys.weatherStartupWarmup(today);

        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] 서버 기동 워밍업 시작 - {} ~ {}, lockKey={}", startDate, endDate, lockKey);
                    refreshRange(startDate, endDate);
                    log.info("[기상 스냅샷] 서버 기동 워밍업 종료 - lockKey={}", lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] 다른 인스턴스가 서버 기동 워밍업을 처리 중이므로 건너뜁니다. - lockKey={}", lockKey);
        }
    }

    @Scheduled(cron = "${weather.scheduler.cron:0 0 * * * *}")
    public void refreshTodayAndFutureDays() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(refreshFutureDays);
        String lockKey = RedisLockKeys.weatherHourlyRefresh(LocalDateTime.now());

        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] 정기 갱신 시작 - {} ~ {}, lockKey={}", today, endDate, lockKey);
                    refreshRange(today, endDate);
                    log.info("[기상 스냅샷] 정기 갱신 종료 - lockKey={}", lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] 다른 인스턴스가 정기 갱신을 처리 중이므로 건너뜁니다. - lockKey={}", lockKey);
        }
    }

    private void refreshRange(LocalDate startDate, LocalDate endDate) {
        LocalDate cursor = startDate;

        while (!cursor.isAfter(endDate)) {
            try {
                weatherInfoService.refreshSnapshot(cursor);
            } catch (Exception e) {
                log.warn("[기상 스냅샷] 갱신 실패 - date={}, message={}", cursor, e.getMessage());
            }

            cursor = cursor.plusDays(1);
        }
    }
}