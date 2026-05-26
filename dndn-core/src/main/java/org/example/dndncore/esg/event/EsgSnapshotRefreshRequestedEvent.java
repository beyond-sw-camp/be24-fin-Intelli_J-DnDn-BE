package org.example.dndncore.esg.event;

import java.time.LocalDate;

public record EsgSnapshotRefreshRequestedEvent(
        Scope scope,
        Long projectId,
        LocalDate reportDate
) {

    public enum Scope {
        PROJECT_DATE,
        DATE
    }

    public static EsgSnapshotRefreshRequestedEvent projectDate(Long projectId, LocalDate reportDate) {
        return new EsgSnapshotRefreshRequestedEvent(Scope.PROJECT_DATE, projectId, reportDate);
    }

    public static EsgSnapshotRefreshRequestedEvent date(LocalDate reportDate) {
        return new EsgSnapshotRefreshRequestedEvent(Scope.DATE, null, reportDate);
    }
}
