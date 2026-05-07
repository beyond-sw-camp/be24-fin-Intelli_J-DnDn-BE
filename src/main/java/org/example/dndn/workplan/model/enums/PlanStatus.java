package org.example.dndn.workplan.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PlanStatus {
    PLANNED("진행 예정"),
    IN_PROGRESS("진행 중");

    private final String label;

    public static PlanStatus fromLabel(String label) {
        if (label == null) {
            return PLANNED;
        }

        for (PlanStatus status : values()) {
            if (status.label.equals(label)) {
                return status;
            }
        }

        return PLANNED;
    }
}