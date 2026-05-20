package org.example.dndncore.weather.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.ai.extractor.WeatherAnalysisExtractor;
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
    private final WeatherAnalysisExtractor weatherAnalysisExtractor;
    private final RedisDistributedLockExecutor redisDistributedLockExecutor;

    @Value("${weather.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${weather.scheduler.warmup-on-startup:true}")
    private boolean warmupOnStartup;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled || !warmupOnStartup) {
            return;
        }

        LocalDate today = LocalDate.now();
        String lockKey = RedisLockKeys.weatherStartupWarmup(today);

        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] 서버 기동 당일 갱신 시작 - date={}, lockKey={}", today, lockKey);
                    refreshTodayWeatherAndAi(today);
                    log.info("[기상 스냅샷] 서버 기동 당일 갱신 종료 - lockKey={}", lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] 다른 인스턴스가 서버 기동 당일 갱신을 처리 중이므로 건너뜁니다. - lockKey={}", lockKey);
        }
    }

    @Scheduled(cron = "${weather.scheduler.cron:0 0 * * * *}")
    public void refreshTodayWeatherAndAiHourly() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        String lockKey = RedisLockKeys.weatherHourlyRefresh(LocalDateTime.now());

        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] 정기 당일 갱신 시작 - date={}, lockKey={}", today, lockKey);
                    refreshTodayWeatherAndAi(today);
                    log.info("[기상 스냅샷] 정기 당일 갱신 종료 - lockKey={}", lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] 다른 인스턴스가 정기 당일 갱신을 처리 중이므로 건너뜁니다. - lockKey={}", lockKey);
        }
    }

    private void refreshTodayWeatherAndAi(LocalDate today) {
        weatherInfoService.refreshTodaySnapshotAndAvailableForecasts();
        weatherAnalysisExtractor.refreshTodayAnalysis(today);
    }
}
