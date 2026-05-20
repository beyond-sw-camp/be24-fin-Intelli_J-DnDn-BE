package org.example.dndncore.esg.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.redis.lock.RedisDistributedLockExecutor;
import org.example.dndncore.redis.lock.RedisLockKeys;
import org.example.dndncore.esg.EsgDailyRolloverService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsgDailyRolloverScheduler {

    private static final long LOCK_WAIT_TIME_SECONDS = 0L;
    private static final long LOCK_LEASE_TIME_SECONDS = 1_800L;

    private final EsgDailyRolloverService esgDailyRolloverService;
    private final RedisDistributedLockExecutor redisDistributedLockExecutor;

    @Value("${esg.daily-rollover.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${esg.daily-rollover.cron:0 0 0 * * *}")
    public void rolloverAtMidnight() {
        if (!enabled) {
            return;
        }

        LocalDate targetDate = LocalDate.now();
        String lockKey = RedisLockKeys.esgDailyRollover(targetDate);
        boolean executed = redisDistributedLockExecutor.execute(
                lockKey,
                LOCK_WAIT_TIME_SECONDS,
                LOCK_LEASE_TIME_SECONDS,
                () -> {
                    log.info("[ESG 일일 마감] 00시 롤오버 시작 - lockKey={}", lockKey);
                    EsgDailyRolloverService.RolloverResult result = esgDailyRolloverService.rolloverToday();
                    log.info(
                            "[ESG 일일 마감] 00시 롤오버 종료 - 대상현장={}, 신규현장스냅샷={}, 신규구역스냅샷={}, 신규입력초기화={}, 건너뜀={}",
                            result.targetProjectCount(),
                            result.createdSiteSnapshotCount(),
                            result.createdZoneSnapshotCount(),
                            result.createdMetricInputCount(),
                            result.skippedProjectCount()
                    );
                }
        );

        if (!executed) {
            log.info("[ESG 일일 마감] 다른 인스턴스가 롤오버를 처리 중이므로 건너뜁니다. - lockKey={}", lockKey);
        }
    }
}
