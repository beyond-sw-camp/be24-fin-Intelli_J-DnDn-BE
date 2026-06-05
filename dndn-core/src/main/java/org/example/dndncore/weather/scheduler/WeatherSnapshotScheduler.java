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
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherSnapshotScheduler {

    private static final long LOCK_WAIT_TIME_SECONDS = 0L;
    private static final long STARTUP_LOCK_LEASE_TIME_SECONDS = 300L;
    private static final long HOURLY_LOCK_LEASE_TIME_SECONDS = 240L;
    private static final long STARTUP_RETRY_DELAY_MILLIS = 15_000L;

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
        boolean executed = executeStartupRefresh(today, "서버 기동 당일 갱신");

        if (!executed) {
            log.info("[기상 스냅샷] 서버 기동 당일 갱신 1차 시도가 실행되지 않았습니다. 오늘 snapshot 최신 여부 확인 후 1회 재시도합니다. - date={}", today);
            retryStartupRefreshIfTodaySnapshotIsStale(today);
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
                HOURLY_LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] 정기 당일 갱신 시작 - date={}, lockKey={}", today, lockKey);
                    refreshTodayWeatherAndAi(today);
                    log.info("[기상 스냅샷] 정기 당일 갱신 종료 - lockKey={}", lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] 정기 당일 갱신 락을 획득하지 못해 건너뜁니다. - lockKey={}", lockKey);
        }
    }

    private boolean executeStartupRefresh(LocalDate today, String label) {
        String lockKey = RedisLockKeys.weatherStartupWarmup(today);

        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                STARTUP_LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[기상 스냅샷] {} 시작 - date={}, lockKey={}", label, today, lockKey);
                    refreshTodayWeatherAndAi(today);
                    log.info("[기상 스냅샷] {} 종료 - lockKey={}", label, lockKey);
                }
        );

        if (!executed) {
            log.info("[기상 스냅샷] {} 락을 획득하지 못해 건너뜁니다. - lockKey={}", label, lockKey);
        }

        return executed;
    }

    private void retryStartupRefreshIfTodaySnapshotIsStale(LocalDate today) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(STARTUP_RETRY_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[기상 스냅샷] 서버 기동 재시도 대기 중 인터럽트 발생 - date={}", today);
                return;
            }

            if (!enabled || !warmupOnStartup) {
                return;
            }

            if (weatherInfoService.hasFreshTodaySnapshot(today)) {
                log.info("[기상 스냅샷] 오늘 snapshot이 이미 최신입니다. 서버 기동 재시도를 생략합니다. - date={}", today);
                return;
            }

            log.warn("[기상 스냅샷] 오늘 snapshot이 아직 갱신되지 않았습니다. 서버 기동 당일 갱신을 1회 재시도합니다. - date={}", today);
            boolean retried = executeStartupRefresh(today, "서버 기동 당일 갱신 재시도");

            if (!retried) {
                log.warn("[기상 스냅샷] 서버 기동 당일 갱신 재시도도 실행되지 않았습니다. 다음 정기 스케줄 또는 Redis 락 상태를 확인하세요. - date={}", today);
            }
        });
    }

    private void refreshTodayWeatherAndAi(LocalDate today) {
        weatherInfoService.refreshTodaySnapshotAndAvailableForecasts();
        weatherAnalysisExtractor.refreshTodayAnalysisForActiveProjects(today);
    }
}
