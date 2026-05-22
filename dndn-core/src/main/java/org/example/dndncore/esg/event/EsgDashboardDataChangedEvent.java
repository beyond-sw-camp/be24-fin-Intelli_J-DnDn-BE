package org.example.dndncore.esg.event;

import java.time.LocalDate;

public record EsgDashboardDataChangedEvent(
        Long projectId,
        LocalDate reportDate,
        Scope scope
) {

    public enum Scope {
        PROJECT_DATE,
        DATE
    }

    public static EsgDashboardDataChangedEvent projectDate(Long projectId, LocalDate reportDate) {
        return new EsgDashboardDataChangedEvent(projectId, reportDate, Scope.PROJECT_DATE);
    }

    public static EsgDashboardDataChangedEvent date(LocalDate reportDate) {
        return new EsgDashboardDataChangedEvent(null, reportDate, Scope.DATE);
    }
}
