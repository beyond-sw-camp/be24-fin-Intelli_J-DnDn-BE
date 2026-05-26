package org.example.dndncore.esg.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dndncore.esg.EsgSnapshotRefreshService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class EsgSnapshotRefreshEventListener {

    private final EsgSnapshotRefreshService esgSnapshotRefreshService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(EsgSnapshotRefreshRequestedEvent event) {
        if (event == null || event.reportDate() == null || event.scope() == null) {
            return;
        }

        try {
            if (event.scope() == EsgSnapshotRefreshRequestedEvent.Scope.DATE) {
                esgSnapshotRefreshService.refreshDate(event.reportDate());
                return;
            }

            esgSnapshotRefreshService.refreshProjectDate(event.projectId(), event.reportDate());
        } catch (Exception exception) {
            log.warn(
                    "[ESG 스냅샷] 이벤트 기반 갱신 실패 - scope={}, projectId={}, reportDate={}",
                    event.scope(),
                    event.projectId(),
                    event.reportDate(),
                    exception
            );
        }
    }
}
