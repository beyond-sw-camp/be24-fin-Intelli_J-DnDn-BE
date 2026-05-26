package org.example.dndncore.esg.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class EsgSnapshotRefreshEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishProjectDate(Long projectId, LocalDate reportDate) {
        if (projectId == null || reportDate == null) {
            return;
        }
        applicationEventPublisher.publishEvent(
                EsgSnapshotRefreshRequestedEvent.projectDate(projectId, reportDate)
        );
    }

    public void publishDate(LocalDate reportDate) {
        if (reportDate == null) {
            return;
        }
        applicationEventPublisher.publishEvent(
                EsgSnapshotRefreshRequestedEvent.date(reportDate)
        );
    }
}
