package org.example.dndncore.worker.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.worker.model.dto.WorkerDto;
import org.example.dndncore.worker.service.WorkerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerSyncScheduler {

    private final WorkerService workerService;

    @Value("${worker.sync.scheduler.enabled:true}")
    private boolean enabled;

    // 매일 오전 00:30
    @Scheduled(cron = "${worker.sync.scheduler.cron:0 30 0 * * *}")
    public void syncAllSites() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        log.info("[인력 동기화 스케줄러] 시작 - date={}", today);

        WorkerDto.BulkSyncRes result = workerService.syncAllSites(today);

        result.getResults().forEach(r -> {
            if (r.isSuccess()) {
                log.info("[인력 동기화 스케줄러] 완료 - siteCode={}, created={}, updated={}, total={}",
                        r.getSiteCode(),
                        r.getDetail().getCreated(),
                        r.getDetail().getUpdated(),
                        r.getDetail().getTotal());
            } else {
                log.warn("[인력 동기화 스케줄러] 실패 - siteCode={}, msg={}", r.getSiteCode(), r.getErrorMessage());
            }
        });

        log.info("[인력 동기화 스케줄러] 종료 - siteCount={}", result.getSiteCount());
    }
}
