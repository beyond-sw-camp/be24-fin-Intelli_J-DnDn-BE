package org.example.dndncore.esg.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.esg.EsgDailyRolloverService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsgDailyRolloverScheduler {

    private final EsgDailyRolloverService esgDailyRolloverService;

    @Value("${esg.daily-rollover.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${esg.daily-rollover.cron:0 0 0 * * *}")
    public void rolloverAtMidnight() {
        if (!enabled) {
            return;
        }

        log.info("[ESG 일일 마감] 00시 롤오버 시작");
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
}
