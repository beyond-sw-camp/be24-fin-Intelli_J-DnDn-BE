package org.example.dndncore.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScheduleChangeStatus {
    PENDING("승인 대기"),
    APPROVED("승인 완료"),
    APPLIED("일정 반영 완료"),
    REJECTED("반려");

    private final String label;

    public static ScheduleChangeStatus fromLabel(String label) {
        if (label == null) return PENDING;
        for (ScheduleChangeStatus s : values()) {
            if (s.label.equals(label)) return s;
        }
        return PENDING;
    }
}