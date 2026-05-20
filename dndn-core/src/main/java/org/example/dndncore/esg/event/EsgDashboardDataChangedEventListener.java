package org.example.dndncore.esg.event;

import lombok.RequiredArgsConstructor;
import org.example.dndncore.esg.cache.EsgDashboardCacheEvictor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EsgDashboardDataChangedEventListener {

    private final EsgDashboardCacheEvictor esgDashboardCacheEvictor;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(EsgDashboardDataChangedEvent event) {
        if (event == null || event.reportDate() == null || event.scope() == null) {
            return;
        }

        if (event.scope() == EsgDashboardDataChangedEvent.Scope.DATE) {
            esgDashboardCacheEvictor.evictDate(event.reportDate());
            return;
        }

        esgDashboardCacheEvictor.evictProjectDate(event.projectId(), event.reportDate());
    }
}
